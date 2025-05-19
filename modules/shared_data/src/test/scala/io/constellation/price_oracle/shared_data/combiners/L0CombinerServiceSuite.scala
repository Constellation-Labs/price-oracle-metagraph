package io.constellation.price_oracle.shared_data.combiners

import java.security.KeyPair

import cats.data.{NonEmptyList, NonEmptySet}
import cats.effect.{IO, Resource}
import cats.syntax.applicative._
import cats.syntax.option._

import scala.collection.immutable.{SortedMap, SortedSet}
import scala.concurrent.duration.DurationInt

import io.constellationnetwork.currency.dataApplication.{DataState, L0NodeContext}
import io.constellationnetwork.currency.schema.currency.{CurrencyIncrementalSnapshot, CurrencySnapshotInfo}
import io.constellationnetwork.ext.cats.effect.ResourceIO
import io.constellationnetwork.json.JsonSerializer
import io.constellationnetwork.schema.ID.Id
import io.constellationnetwork.schema._
import io.constellationnetwork.schema.artifact.PricingUpdate
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.schema.height.{Height, SubHeight}
import io.constellationnetwork.schema.peer.PeerId
import io.constellationnetwork.schema.priceOracle.PriceFraction
import io.constellationnetwork.schema.priceOracle.TokenPair.DAG_USD
import io.constellationnetwork.schema.swap.CurrencyId
import io.constellationnetwork.security._
import io.constellationnetwork.security.hash.{Hash, ProofsHash}
import io.constellationnetwork.security.hex.Hex
import io.constellationnetwork.security.signature.Signed
import io.constellationnetwork.security.signature.signature.{Signature, SignatureProof}

import ciris.Secret
import eu.timepit.refined.types.numeric.{NonNegLong, PosLong}
import io.constellation.price_oracle.shared_data.app.ApplicationConfig
import io.constellation.price_oracle.shared_data.app.ApplicationConfig._
import io.constellation.price_oracle.shared_data.pricefeed.PriceFeedId
import io.constellation.price_oracle.shared_data.pricefeed.PriceFeedId.GateIO
import io.constellation.price_oracle.shared_data.types._
import org.http4s.Uri
import weaver.MutableIOSuite

object L0CombinerServiceSuite extends MutableIOSuite {

  override type Res = (Hasher[IO], SecurityProvider[IO], L0NodeContext[IO], L0CombinerService[IO], KeyPair)

  override def sharedResource: Resource[IO, Res] =
    for {
      implicit0(sp: SecurityProvider[IO]) <- SecurityProvider.forAsync[IO]
      implicit0(j: JsonSerializer[IO]) <- JsonSerializer.forSync[IO].asResource
      implicit0(jh: Hasher[IO]) = Hasher.forJson[IO]
      context <- mkL0NodeContext(sp).asResource
      combiner <- L0CombinerService.make[IO](config).pure[IO].asResource
      kp <- KeyPairGenerator.makeKeyPair[IO].asResource

    } yield (jh, sp, context, combiner, kp)

  test("combine calculated state") { res =>
    implicit val (hasher, sp, context, combiner, kp) = res

    for {
      priceUpdate1 <- priceUpdate(1).pure[IO]
      priceUpdate2 = priceUpdate(2)
      priceUpdate3 = priceUpdate(3)
      priceUpdate4 = priceUpdate(4)

      signed3 <- signed(priceUpdate3)
      signed4 <- signed(priceUpdate4)

      updates = List(signed3, signed4)

      oldState: DataState[PriceOracleOnChainState, PriceOracleCalculatedState] =
        DataState(
          PriceOracleOnChainState(),
          PriceOracleCalculatedState(Map(DAG_USD -> Map(signed3.proofs.head.id -> List(priceUpdate1, priceUpdate2))))
        )

      newState <- combiner.combine(oldState, updates)
    } yield
      expect.all(newState.calculated.priceState(DAG_USD) == Map(signed3.proofs.head.id -> List(priceUpdate2, priceUpdate3, priceUpdate4)))

  }

  test("combine shared artifacts") { res =>
    implicit val (hasher, sp, context, combiner, kp) = res

    for {
      priceUpdate1 <- priceUpdate(1).pure[IO]
      priceUpdate2 = priceUpdate(2)
      priceUpdate3 = priceUpdate(3)
      priceUpdate4 = priceUpdate(4)

      signed3 <- signed(priceUpdate3)
      signed4 <- signed(priceUpdate4)

      updates = List(signed3, signed4)

      oldState: DataState[PriceOracleOnChainState, PriceOracleCalculatedState] =
        DataState(
          PriceOracleOnChainState(),
          PriceOracleCalculatedState(Map(DAG_USD -> Map(signed3.proofs.head.id -> List(priceUpdate1, priceUpdate2))))
        )

      newState <- combiner.combine(oldState, updates)
    } yield
      expect.all(
        newState.sharedArtifacts == SortedSet(
          PricingUpdate(PriceFraction(DAG_USD, NonNegFraction(NonNegLong(37209302L), PosLong(100000000L))))
        )
      )
  }

  test("remove previous shared artifacts") { res =>
    implicit val (hasher, sp, context, combiner, kp) = res

    val priceUpdate1 = priceUpdate(1)
    val priceUpdate2 = priceUpdate(2)

    val oldState: DataState[PriceOracleOnChainState, PriceOracleCalculatedState] =
      DataState(PriceOracleOnChainState(), PriceOracleCalculatedState())

    val priceUpdates1 = Signed.forAsyncHasher(priceUpdate1, kp).map(List(_))
    val priceUpdates2 = Signed.forAsyncHasher(priceUpdate2, kp).map(List(_))

    priceUpdates1.flatMap { updates1 =>
      priceUpdates2.flatMap { updates2 =>
        combiner.combine(oldState, updates1).flatMap { newState1 =>
          combiner.combine(newState1, updates2).map { newState2 =>
            expect.all(
              newState1.sharedArtifacts == SortedSet(
                PricingUpdate(PriceFraction(DAG_USD, NonNegFraction(NonNegLong(100000000L), PosLong(100000000L))))
              ),
              newState2.sharedArtifacts == SortedSet(
                PricingUpdate(PriceFraction(DAG_USD, NonNegFraction(NonNegLong(80000000L), PosLong(100000000L))))
              )
            )

          }
        }
      }
    }
  }

  private def priceUpdate(n: Int, priceFeedId: PriceFeedId = GateIO): PriceUpdate =
    PriceUpdate(DAG_USD, PriceValues.of(PriceValue(priceFeedId, BigDecimal(n))), EpochProgress.MinValue)

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
        minEpochsBetweenUpdates = NonNegLong(1),
        asOfEpoch = EpochProgress.MinValue
      )
    ),
    seedlist = List.empty
  )

  private def mkL0NodeContext(sp: SecurityProvider[IO]) =
    new L0NodeContext[IO] {
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

      override def getLastSynchronizedGlobalSnapshotCombined: IO[Option[(Hashed[GlobalIncrementalSnapshot], GlobalSnapshotInfo)]] =
        none.pure[IO]

      override def getLastCurrencySnapshot: IO[Option[Hashed[CurrencyIncrementalSnapshot]]] = none.pure[IO]

      override def getCurrencySnapshot(ordinal: SnapshotOrdinal): IO[Option[Hashed[CurrencyIncrementalSnapshot]]] = none.pure[IO]

      override def getLastCurrencySnapshotCombined: IO[Option[(Hashed[CurrencyIncrementalSnapshot], CurrencySnapshotInfo)]] = none.pure[IO]

      override def securityProvider: SecurityProvider[IO] = sp

      override def getCurrencyId: IO[CurrencyId] = ???
    }.pure[IO]

  private def signed(u: PriceUpdate)(implicit hasher: Hasher[IO], sp: SecurityProvider[IO], kp: KeyPair): IO[Signed[PriceUpdate]] =
    Signed.forAsyncHasher(u, kp)

}
