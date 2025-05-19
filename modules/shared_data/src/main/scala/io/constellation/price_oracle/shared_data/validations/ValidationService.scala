package io.constellation.price_oracle.shared_data.validations

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._

import io.constellationnetwork.currency.dataApplication._
import io.constellationnetwork.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import io.constellationnetwork.schema.ID.Id
import io.constellationnetwork.schema.address.Address
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.security.signature.Signed
import io.constellationnetwork.security.{Hasher, SecurityProvider}

import eu.timepit.refined.cats.refTypeOrder
import io.constellation.price_oracle.shared_data.app.ApplicationConfig
import io.constellation.price_oracle.shared_data.types.{PriceOracleCalculatedState, PriceOracleOnChainState, PriceUpdate}

trait ValidationService[F[_]] {
  def validateUpdate(
    update: PriceUpdate
  )(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]]

  def validateData(
    signedUpdates: NonEmptyList[Signed[PriceUpdate]],
    state: DataState[PriceOracleOnChainState, PriceOracleCalculatedState]
  )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]]
}

object ValidationService {
  def make[F[_]: Async: SecurityProvider: Hasher](
    applicationConfig: ApplicationConfig
  ): ValidationService[F] = new ValidationService[F] {
    override def validateUpdate(update: PriceUpdate)(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
      validatePrices(update).pure[F]

    private def validateL0(
      signedUpdate: Signed[PriceUpdate],
      state: DataState[PriceOracleOnChainState, PriceOracleCalculatedState]
    )(
      implicit context: L0NodeContext[F]
    ): F[DataApplicationValidationErrorOr[Unit]] =
      for {
        epochR <- validateEpochProgress(signedUpdate, state.calculated)
        address <- signedUpdate.proofs.head.id.toAddress
        signedR <- signatureValidations(signedUpdate, address)
        pricesR = validatePrices(signedUpdate.value)
        seedlistR = validateSeedList(signedUpdate)
      } yield signedR.productR(pricesR).productR(epochR).productR(seedlistR)

    override def validateData(
      signedUpdates: NonEmptyList[Signed[PriceUpdate]],
      state: DataState[PriceOracleOnChainState, PriceOracleCalculatedState]
    )(
      implicit context: L0NodeContext[F]
    ): F[DataApplicationValidationErrorOr[Unit]] = signedUpdates.traverse(validateL0(_, state)).map(_.combineAll)

    private def isSignedExclusivelyBySourceValidation(
      signed: Signed[PriceUpdate],
      sourceAddress: Address
    ): F[DataApplicationValidationErrorOr[Unit]] =
      signed.proofs.head.id.toAddress.map(signerAddress => NotSignedExclusivelyBySourceAddress.unlessA(signerAddress === sourceAddress))

    private def validateHasSingleSignature[A](signed: Signed[A]): DataApplicationValidationErrorOr[Unit] = {
      val maxSignatureCount = 1L
      MultipleSignatures.unlessA(signed.proofs.size === maxSignatureCount)
    }

    private def signatureValidations(
      signed: Signed[PriceUpdate],
      sourceAddress: Address
    ): F[DataApplicationValidationErrorOr[Unit]] = for {
      exclusivelySignedBySourceAddress <- isSignedExclusivelyBySourceValidation(signed, sourceAddress)
      singleSignatureValidation = validateHasSingleSignature(signed)
    } yield
      singleSignatureValidation
        .productR(exclusivelySignedBySourceAddress)

    private val zero = BigDecimal(0)
    private def validatePrices(update: PriceUpdate): DataApplicationValidationErrorOr[Unit] =
      InvalidPrice.unlessA(update.prices.prices.forall(_.price >= zero))

    private def validateSeedList(signedUpdate: Signed[PriceUpdate]): DataApplicationValidationErrorOr[Unit] = {
      val nodeId = signedUpdate.proofs.head.id
      UnknownPeer(nodeId).unlessA(applicationConfig.seedlist.isEmpty || applicationConfig.seedlist.contains(nodeId))
    }

    private def validateEpochProgress(
      signedUpdate: Signed[PriceUpdate],
      state: PriceOracleCalculatedState
    ): F[DataApplicationValidationErrorOr[Unit]] = {
      val lastEpoch = state.priceState
        .get(signedUpdate.tokenPair)
        .flatMap(_.get(signedUpdate.proofs.head.id))
        .flatMap(_.lastOption.map(_.epochProgress))
        .getOrElse(EpochProgress.MinValue)
      applicationConfig.intervalsConfig(signedUpdate.epochProgress).map { intervalsConfig =>
        signedUpdate.epochProgress.minus(lastEpoch) match {
          case Right(diff) =>
            TooFrequentUpdate(lastEpoch, signedUpdate.epochProgress).unlessA(diff.value >= intervalsConfig.minEpochsBetweenUpdates)
          case Left(_) => EpochUnderflow(lastEpoch, signedUpdate.epochProgress).invalidNec
        }
      }
    }
  }

  case object MultipleSignatures extends DataApplicationValidationError {
    val message = "Multiple signatures"
  }

  case object NotSignedExclusivelyBySourceAddress extends DataApplicationValidationError {
    val message = "Not signed exclusively by address owner"
  }

  case object InvalidPrice extends DataApplicationValidationError {
    val message = "Invalid price"
  }

  case class UnknownPeer(nodeId: Id) extends DataApplicationValidationError {
    val message = s"Unknown peer $nodeId"
  }

  case class TooFrequentUpdate(lastEpoch: EpochProgress, currentEpoch: EpochProgress) extends DataApplicationValidationError {
    override val message: String = s"Too frequent update (last epoch: $lastEpoch, current epoch: $currentEpoch)"
  }

  case class EpochUnderflow(lastEpoch: EpochProgress, currentEpoch: EpochProgress) extends DataApplicationValidationError {
    override val message: String = s"Epoch underflow (last epoch: $lastEpoch, current epoch: $currentEpoch)"
  }

  private type DataApplicationValidationType = DataApplicationValidationErrorOr[Unit]

  val valid: DataApplicationValidationType = ().validNec[DataApplicationValidationError]

  implicit class DataApplicationValidationTypeOps[E <: DataApplicationValidationError](err: E) {
    def invalid: DataApplicationValidationType = err.invalidNec[Unit]

    def unlessA(cond: Boolean): DataApplicationValidationType = if (cond) valid else invalid

    def whenA(cond: Boolean): DataApplicationValidationType = if (cond) invalid else valid
  }
}
