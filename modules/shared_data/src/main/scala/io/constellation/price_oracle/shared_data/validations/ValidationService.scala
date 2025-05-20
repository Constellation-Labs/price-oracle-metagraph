package io.constellation.price_oracle.shared_data.validations

import cats.data.NonEmptyList
import cats.effect.Async
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxValidatedIdBinCompat0}
import cats.syntax.all._

import io.constellationnetwork.currency.dataApplication._
import io.constellationnetwork.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import io.constellationnetwork.schema.address.Address
import io.constellationnetwork.security.SecurityProvider
import io.constellationnetwork.security.signature.Signed

import io.constellation.price_oracle.shared_data.app.ApplicationConfig
import io.constellation.price_oracle.shared_data.types.codecs.HasherSelector
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
  def make[F[_]: Async: SecurityProvider: HasherSelector](
    applicationConfig: ApplicationConfig
  ): ValidationService[F] = new ValidationService[F] {
    override def validateUpdate(update: PriceUpdate)(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
      valid.pure[F]

    private def validateL0(
      signedUpdate: Signed[PriceUpdate],
      state: DataState[PriceOracleOnChainState, PriceOracleCalculatedState]
    )(
      implicit context: L0NodeContext[F]
    ): F[DataApplicationValidationErrorOr[Unit]] =
      for {
        address <- signedUpdate.proofs.head.id.toAddress
        signedR <- signatureValidations(signedUpdate, address)
      } yield signedR

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

    def signatureValidations(
      signed: Signed[PriceUpdate],
      sourceAddress: Address
    ): F[DataApplicationValidationErrorOr[Unit]] = for {
      exclusivelySignedBySourceAddress <- isSignedExclusivelyBySourceValidation(signed, sourceAddress)
      singleSignatureValidation = validateHasSingleSignature(signed)
    } yield
      singleSignatureValidation
        .productR(exclusivelySignedBySourceAddress)

    case object MultipleSignatures extends DataApplicationValidationError {
      val message = "Multiple signatures"
    }

    case object NotSignedExclusivelyBySourceAddress extends DataApplicationValidationError {
      val message = "Not signed exclusively by address owner"
    }

    private type DataApplicationValidationType = DataApplicationValidationErrorOr[Unit]

    val valid: DataApplicationValidationType = ().validNec[DataApplicationValidationError]

    implicit class DataApplicationValidationTypeOps[E <: DataApplicationValidationError](err: E) {
      def invalid: DataApplicationValidationType = err.invalidNec[Unit]

      def unlessA(cond: Boolean): DataApplicationValidationType = if (cond) valid else invalid

      def whenA(cond: Boolean): DataApplicationValidationType = if (cond) invalid else valid
    }

  }
}
