package io.constellation.price_oracle.shared_data.validations

import cats.data.NonEmptyList
import cats.effect.Async

import io.constellationnetwork.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import io.constellationnetwork.currency.dataApplication.{L0NodeContext, L1NodeContext}
import io.constellationnetwork.security.SecurityProvider
import io.constellationnetwork.security.signature.Signed

import io.constellation.price_oracle.shared_data.app.ApplicationConfig
import io.constellation.price_oracle.shared_data.types.codecs.HasherSelector
import io.constellation.price_oracle.shared_data.types.{CalculatedState, PriceUpdate}

trait ValidationService[F[_]] {
  def validateUpdate(
    update: PriceUpdate
  )(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]]

  def validateData(
    signedUpdates: NonEmptyList[Signed[PriceUpdate]],
    state: CalculatedState
  )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]]
}

object ValidationService {
  def make[F[_]: Async: SecurityProvider: HasherSelector](
    applicationConfig: ApplicationConfig
  ): ValidationService[F] = new ValidationService[F] {
    override def validateUpdate(update: PriceUpdate)(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] = ???

    override def validateData(signedUpdates: NonEmptyList[Signed[PriceUpdate]], state: CalculatedState)(
      implicit context: L0NodeContext[F]
    ): F[DataApplicationValidationErrorOr[Unit]] = ???
  }

}
