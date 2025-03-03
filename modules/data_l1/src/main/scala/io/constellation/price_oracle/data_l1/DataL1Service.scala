package io.constellation.price_oracle.data_l1

import cats.data.NonEmptyList
import cats.effect.Async

import io.constellationnetwork.currency.dataApplication._
import io.constellationnetwork.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import io.constellationnetwork.routes.internal.ExternalUrlPrefix
import io.constellationnetwork.security.Hashed
import io.constellationnetwork.security.signature.Signed

import io.circe.{Decoder, Encoder}
import io.constellation.price_oracle.shared_data.types.{PriceOracleCalculatedState, PriceOracleOnChainState, PriceUpdate}
import io.constellation.price_oracle.shared_data.validations.ValidationService
import org.http4s._

object DataL1Service {

  def make[F[+_]: Async](validationService: ValidationService[F]): BaseDataApplicationL1Service[F] = BaseDataApplicationL1Service(
    new DataApplicationL1Service[F, PriceUpdate, PriceOracleOnChainState, PriceOracleCalculatedState] {
      override def validateUpdate(update: PriceUpdate)(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] = ???

      override def serializeState(state: PriceOracleOnChainState): F[Array[Byte]] = ???

      override def deserializeState(bytes: Array[Byte]): F[Either[Throwable, PriceOracleOnChainState]] = ???

      override def serializeUpdate(update: PriceUpdate): F[Array[Byte]] = ???

      override def deserializeUpdate(bytes: Array[Byte]): F[Either[Throwable, PriceUpdate]] = ???

      override def serializeBlock(block: Signed[dataApplication.DataApplicationBlock]): F[Array[Byte]] = ???

      override def deserializeBlock(bytes: Array[Byte]): F[Either[Throwable, Signed[dataApplication.DataApplicationBlock]]] = ???

      override def serializeCalculatedState(state: PriceOracleCalculatedState): F[Array[Byte]] = ???

      override def deserializeCalculatedState(bytes: Array[Byte]): F[Either[Throwable, PriceOracleCalculatedState]] = ???

      override def dataEncoder: Encoder[PriceUpdate] = ???

      override def dataDecoder: Decoder[PriceUpdate] = ???

      override def signedDataEntityDecoder: EntityDecoder[F, Signed[PriceUpdate]] = ???

      override def calculatedStateEncoder: Encoder[PriceOracleCalculatedState] = ???

      override def calculatedStateDecoder: Decoder[PriceOracleCalculatedState] = ???

      override def routes(implicit context: L1NodeContext[F]): HttpRoutes[F] = ???
    }
  )
}
