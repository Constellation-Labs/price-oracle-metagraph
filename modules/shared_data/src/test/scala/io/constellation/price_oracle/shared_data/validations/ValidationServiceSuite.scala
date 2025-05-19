package io.constellation.price_oracle.shared_data.validations

import java.security.KeyPair

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, NonEmptySet}
import cats.effect.{IO, Resource}
import cats.implicits.catsSyntaxValidatedIdBinCompat0
import cats.syntax.all._

import scala.collection.immutable.{SortedMap, SortedSet}
import scala.concurrent.duration.DurationInt

import io.constellationnetwork.currency.dataApplication.{DataState, L0NodeContext, L1NodeContext}
import io.constellationnetwork.currency.schema.currency
import io.constellationnetwork.ext.cats.effect.ResourceIO
import io.constellationnetwork.json.JsonSerializer
import io.constellationnetwork.schema.ID.Id
import io.constellationnetwork.schema._
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.schema.height.{Height, SubHeight}
import io.constellationnetwork.schema.peer.PeerId
import io.constellationnetwork.schema.priceOracle.TokenPair.DAG_USD
import io.constellationnetwork.schema.swap.CurrencyId
import io.constellationnetwork.security._
import io.constellationnetwork.security.hash.{Hash, ProofsHash}
import io.constellationnetwork.security.hex.Hex
import io.constellationnetwork.security.signature.Signed
import io.constellationnetwork.security.signature.signature.{Signature, SignatureProof}

import ciris.Secret
import eu.timepit.refined.types.numeric.NonNegLong
import io.constellation.price_oracle.shared_data.app.ApplicationConfig
import io.constellation.price_oracle.shared_data.app.ApplicationConfig._
import io.constellation.price_oracle.shared_data.pricefeed.PriceFeedId
import io.constellation.price_oracle.shared_data.pricefeed.PriceFeedId.GateIO
import io.constellation.price_oracle.shared_data.types._
import org.http4s.Uri
import weaver.MutableIOSuite

object ValidationServiceSuite extends MutableIOSuite {

  override type Res = (SecurityProvider[IO], Hasher[IO], KeyPair)

  override def sharedResource: Resource[IO, Res] =
    for {
      implicit0(sp: SecurityProvider[IO]) <- SecurityProvider.forAsync[IO]
      implicit0(j: JsonSerializer[IO]) <- JsonSerializer.forSync[IO].asResource
      h = Hasher.forJson[IO]
      kp <- KeyPairGenerator.makeKeyPair[IO].asResource
    } yield (sp, h, kp)

  private val emptyState: DataState[PriceOracleOnChainState, PriceOracleCalculatedState] =
    DataState(PriceOracleOnChainState(), PriceOracleCalculatedState())

  test("L1 validate price") { res =>
    implicit val (sp, h, _) = res
    implicit val context: L1NodeContext[IO] = mkL1NodeContext

    val v = mkValidationService

    for {
      valid0 <- v.validateUpdate(priceUpdate(0))
      valid1 <- v.validateUpdate(priceUpdate(1))
      invalid <- v.validateUpdate(priceUpdate(-1))
    } yield
      expect.all(
        valid0 == Valid(()),
        valid1 == Valid(()),
        invalid == ValidationService.InvalidPrice.invalidNec
      )
  }

  test("L0 validate price") { res =>
    implicit val (sp, h, kp) = res
    implicit val context: L0NodeContext[IO] = mkL0NodeContext

    val v = mkValidationService

    for {
      signed0 <- signed(priceUpdate(0))
      signed1 <- signed(priceUpdate(1))
      signed_1 <- signed(priceUpdate(-1))
      valid0 <- v.validateData(NonEmptyList.of(signed0), emptyState)
      valid1 <- v.validateData(NonEmptyList.of(signed1), emptyState)
      invalid <- v.validateData(NonEmptyList.of(signed_1), emptyState)
    } yield
      expect.all(
        valid0 == Valid(()),
        valid1 == Valid(()),
        invalid == ValidationService.InvalidPrice.invalidNec
      )
  }

  test("L0 validate too frequent update for empty state") { res =>
    implicit val (sp, h, kp) = res
    implicit val context: L0NodeContext[IO] = mkL0NodeContext

    val configWithMinEpochs = config.copy(
      intervals = NonEmptyList.of(
        IntervalsConfig(
          poll = 1.second,
          storage = 3.seconds,
          movingAverage = 3.seconds,
          minEpochsBetweenUpdates = NonNegLong(1),
          asOfEpoch = EpochProgress.MinValue
        )
      )
    )
    val v = ValidationService.make[IO](configWithMinEpochs)

    for {
      signed1 <- signed(priceUpdate(1, epochProgress = EpochProgress(NonNegLong(2))))
      valid <- v.validateData(NonEmptyList.of(signed1), emptyState)
      signed2 <- signed(priceUpdate(1, epochProgress = EpochProgress(NonNegLong(0))))
      invalid <- v.validateData(NonEmptyList.of(signed2), emptyState)
    } yield
      expect.all(
        valid == Valid(()),
        invalid == ValidationService.TooFrequentUpdate(EpochProgress.MinValue, EpochProgress.MinValue).invalidNec
      )
  }

  test("L0 validate too frequent update for state with existing updates") { res =>
    implicit val (sp, h, kp) = res
    implicit val context: L0NodeContext[IO] = mkL0NodeContext

    val configWithMinEpochs = config.copy(
      intervals = NonEmptyList.of(
        IntervalsConfig(
          poll = 1.second,
          storage = 3.seconds,
          movingAverage = 3.seconds,
          minEpochsBetweenUpdates = NonNegLong(2),
          asOfEpoch = EpochProgress.MinValue
        )
      )
    )
    val v = ValidationService.make[IO](configWithMinEpochs)

    for {
      signed0 <- signed(priceUpdate(0, epochProgress = EpochProgress(NonNegLong(5))))
      id = signed0.proofs.head.id

      state =
        DataState(PriceOracleOnChainState(), PriceOracleCalculatedState(priceState = Map(DAG_USD -> Map(id -> List(signed0)))))

      signed1 <- signed(priceUpdate(1, epochProgress = EpochProgress(NonNegLong(6))))
      invalid1 <- v.validateData(NonEmptyList.of(signed1), state)

      signed2 <- signed(priceUpdate(2, epochProgress = EpochProgress(NonNegLong(7))))
      valid <- v.validateData(NonEmptyList.of(signed2), state)

      state =
        DataState(PriceOracleOnChainState(), PriceOracleCalculatedState(priceState = Map(DAG_USD -> Map(id -> List(signed0, signed2)))))

      signed3 <- signed(priceUpdate(3, epochProgress = EpochProgress(NonNegLong(8))))
      invalid2 <- v.validateData(NonEmptyList.of(signed3), state)
    } yield
      expect.all(
        invalid1 == ValidationService.TooFrequentUpdate(EpochProgress(NonNegLong(5)), EpochProgress(NonNegLong(6))).invalidNec,
        valid == Valid(()),
        invalid2 == ValidationService.TooFrequentUpdate(EpochProgress(NonNegLong(7)), EpochProgress(NonNegLong(8))).invalidNec
      )
  }

  test("validateSeedList should validate node ID against seedlist") { res =>
    implicit val (sp, h, kp) = res
    implicit val context: L0NodeContext[IO] = mkL0NodeContext

    // Create a signed update with a specific node ID
    val nodeId = Id(
      Hex(
        "65fe93b27ebccacd4d3ec3e77af7ea266b0dfb6a9b3b14574ab0e4a43a32cb38ce874f22aa96cf4ca51cdc38ced3bd27ba96518237557755216ebb82a64dc719"
      )
    )
    val signedUpdate = Signed(
      priceUpdate(1),
      proofs = NonEmptySet.of(
        SignatureProof(
          id = nodeId,
          signature = Signature(
            Hex(
              "660854ebed89f21c2cb641dcaf8e0422ceb891506ec4e74dc454126e8e59ace2f9e43b6e3947827e6fbfe87c1300bb1145c79434c665569cf8db91cc4bb0fd3e"
            )
          )
        )
      )
    )

    // Test with empty seedlist (should be valid)
    val emptySeedlistConfig = config.copy(seedlist = List.empty)
    val vEmpty = ValidationService.make[IO](emptySeedlistConfig)

    // Test with seedlist containing the node ID (should be valid)
    val validSeedlistConfig = config.copy(seedlist = List(nodeId))
    val vValid = ValidationService.make[IO](validSeedlistConfig)

    // Test with seedlist not containing the node ID (should be invalid)
    val invalidSeedlistConfig = config.copy(seedlist =
      List(
        Id(
          Hex(
            "63262e92ebc38ad48ba070879b54f85b3043c1055157254a297e6ccc8f928da7d1c8a9a522643ec6dc80b05d54c9c1b626eee0b1bf42b3e71a3bb594ad2df638"
          )
        )
      )
    )
    val vInvalid = ValidationService.make[IO](invalidSeedlistConfig)

    for {
      emptyResult <- vEmpty.validateData(NonEmptyList.of(signedUpdate), emptyState)
      validResult <- vValid.validateData(NonEmptyList.of(signedUpdate), emptyState)
      invalidResult <- vInvalid.validateData(NonEmptyList.of(signedUpdate), emptyState)
    } yield
      expect.all(
        emptyResult == Valid(()),
        validResult == Valid(()),
        invalidResult == ValidationService.UnknownPeer(nodeId).invalidNec
      )
  }

  private def signed(update: PriceUpdate)(implicit kp: KeyPair, sp: SecurityProvider[IO], h: Hasher[IO]) = Signed.forAsyncHasher(update, kp)

  private def priceUpdate(n: Int, priceFeedId: PriceFeedId = GateIO, epochProgress: EpochProgress = EpochProgress.MinValue): PriceUpdate =
    PriceUpdate(DAG_USD, PriceValues.of(PriceValue(priceFeedId, BigDecimal(n))), epochProgress)

  private def config: ApplicationConfig = ApplicationConfig(
    dataL1ServerUri = Uri.unsafeFromString("localhost:9000"),
    signing = SigningConfig("", Secret(""), Secret("")),
    environment = Dev,
    priceFeeds = NonEmptyList.of(PriceFeedConfig(DAG_USD, "DAG_USDT".some, "DAGUSDT".some, "DAGUSDT".some)),
    intervals = NonEmptyList.of(
      IntervalsConfig(
        poll = 1.second,
        storage = 3.seconds,
        movingAverage = 3.seconds,
        minEpochsBetweenUpdates = NonNegLong(0),
        asOfEpoch = EpochProgress.MinValue
      )
    ),
    seedlist = List.empty
  )

  private def mkValidationService(implicit sp: SecurityProvider[IO], hasher: Hasher[IO]) = ValidationService.make[IO](config)

  private def mkL0NodeContext = new L0NodeContext[IO] {
    override def getLastSynchronizedGlobalSnapshot: IO[Option[Hashed[GlobalIncrementalSnapshot]]] = Hashed(
      Signed(
        GlobalIncrementalSnapshot(
          ordinal = SnapshotOrdinal.MinValue,
          height = Height.MinValue,
          subHeight = SubHeight.MinValue,
          lastSnapshotHash = Hash.empty,
          blocks = SortedSet(),
          stateChannelSnapshots = SortedMap(),
          rewards = SortedSet(),
          delegateRewards = None,
          epochProgress = EpochProgress.MinValue,
          nextFacilitators = NonEmptyList.of(PeerId.apply(Hex("00"))),
          tips = SnapshotTips(deprecated = SortedSet(), remainedActive = SortedSet()),
          stateProof = GlobalSnapshotStateProof(
            lastStateChannelSnapshotHashesProof = Hash.empty,
            lastTxRefsProof = Hash.empty,
            balancesProof = Hash.empty,
            lastCurrencySnapshotsProof = None,
            activeAllowSpends = None,
            activeTokenLocks = None,
            tokenLockBalances = None,
            lastAllowSpendRefs = None,
            lastTokenLockRefs = None,
            updateNodeParameters = None,
            activeDelegatedStakes = None,
            delegatedStakesWithdrawals = None,
            activeNodeCollaterals = None,
            nodeCollateralWithdrawals = None,
            priceState = None,
            lastGlobalSnapshotsWithCurrency = None
          ),
          allowSpendBlocks = None,
          tokenLockBlocks = None,
          spendActions = None,
          updateNodeParameters = None,
          artifacts = None,
          activeDelegatedStakes = None,
          delegatedStakesWithdrawals = None,
          activeNodeCollaterals = None,
          nodeCollateralWithdrawals = None
        ),
        proofs = NonEmptySet.of(SignatureProof(id = Id(Hex("00")), signature = Signature(Hex("00"))))
      ),
      hash = Hash.empty,
      proofsHash = ProofsHash("")
    ).some.pure[IO]

    override def getLastSynchronizedGlobalSnapshotCombined: IO[Option[(Hashed[GlobalIncrementalSnapshot], GlobalSnapshotInfo)]] = ???

    override def getLastCurrencySnapshot: IO[Option[Hashed[currency.CurrencyIncrementalSnapshot]]] = ???

    override def getCurrencySnapshot(ordinal: SnapshotOrdinal): IO[Option[Hashed[currency.CurrencyIncrementalSnapshot]]] = ???

    override def getLastCurrencySnapshotCombined
      : IO[Option[(Hashed[currency.CurrencyIncrementalSnapshot], currency.CurrencySnapshotInfo)]] = ???

    override def securityProvider: SecurityProvider[IO] = ???

    override def getCurrencyId: IO[CurrencyId] = ???
  }

  private def mkL1NodeContext = new L1NodeContext[IO] {
    override def getLastGlobalSnapshot: IO[Option[Hashed[GlobalIncrementalSnapshot]]] = ???

    override def getLastCurrencySnapshot: IO[Option[Hashed[currency.CurrencyIncrementalSnapshot]]] = ???

    override def getLastCurrencySnapshotCombined
      : IO[Option[(Hashed[currency.CurrencyIncrementalSnapshot], currency.CurrencySnapshotInfo)]] = ???

    override def securityProvider: SecurityProvider[IO] = ???

    override def getCurrencyId: IO[CurrencyId] = ???
  }
}
