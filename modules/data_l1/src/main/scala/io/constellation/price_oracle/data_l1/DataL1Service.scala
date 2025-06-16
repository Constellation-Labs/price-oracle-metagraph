package io.constellation.price_oracle.data_l1

import cats.effect.Async
import cats.syntax.all._

import io.constellationnetwork.currency.dataApplication._
import io.constellationnetwork.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import io.constellationnetwork.json.JsonSerializer
import io.constellationnetwork.security.signature.Signed

import io.circe.{Decoder, Encoder}
import io.constellation.price_oracle.shared_data.types.codecs.DataUpdateCodec._
import io.constellation.price_oracle.shared_data.types.{PriceOracleCalculatedState, PriceOracleOnChainState, PriceUpdate}
import io.constellation.price_oracle.shared_data.validations.ValidationService
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{EntityDecoder, HttpRoutes}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object DataL1Service {

  def make[F[+_]: Async](
    validationService: ValidationService[F],
    jsonSerializer: JsonSerializer[F]
  ): BaseDataApplicationL1Service[F] =
    BaseDataApplicationL1Service(
      new DataApplicationL1Service[F, PriceUpdate, PriceOracleOnChainState, PriceOracleCalculatedState] {
        private def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger

        override def validateUpdate(update: PriceUpdate)(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
          for {
            _ <- logger.info(s"validateUpdate: $update")
            res <- validationService.validateUpdate(update)
          } yield res

        override def serializeState(state: PriceOracleOnChainState): F[Array[Byte]] = jsonSerializer.serialize(state)

        override def deserializeState(bytes: Array[Byte]): F[Either[Throwable, PriceOracleOnChainState]] =
          jsonSerializer.deserialize[PriceOracleOnChainState](bytes)

        override def serializeUpdate(update: PriceUpdate): F[Array[Byte]] = jsonSerializer.serialize(update)

        override def deserializeUpdate(bytes: Array[Byte]): F[Either[Throwable, PriceUpdate]] =
          jsonSerializer.deserialize[PriceUpdate](bytes)

        override def serializeBlock(block: Signed[dataApplication.DataApplicationBlock]): F[Array[Byte]] =
          jsonSerializer.serialize(block)

        override def deserializeBlock(bytes: Array[Byte]): F[Either[Throwable, Signed[dataApplication.DataApplicationBlock]]] =
          jsonSerializer.deserialize[Signed[DataApplicationBlock]](bytes)

        override def serializeCalculatedState(state: PriceOracleCalculatedState): F[Array[Byte]] = jsonSerializer.serialize(state)

        override def deserializeCalculatedState(bytes: Array[Byte]): F[Either[Throwable, PriceOracleCalculatedState]] =
          jsonSerializer.deserialize[PriceOracleCalculatedState](bytes)

        override def dataEncoder: Encoder[PriceUpdate] = implicitly[Encoder[PriceUpdate]]

        override def dataDecoder: Decoder[PriceUpdate] = implicitly[Decoder[PriceUpdate]]

        override def signedDataEntityDecoder: EntityDecoder[F, Signed[PriceUpdate]] = circeEntityDecoder

        override def calculatedStateEncoder: Encoder[PriceOracleCalculatedState] = implicitly[Encoder[PriceOracleCalculatedState]]

        override def calculatedStateDecoder: Decoder[PriceOracleCalculatedState] = implicitly[Decoder[PriceOracleCalculatedState]]

        override def routes(implicit context: L1NodeContext[F]): HttpRoutes[F] = HttpRoutes.empty

      }
    )
}
