package io.constellation.price_oracle.l0

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._

import io.constellationnetwork.currency.dataApplication._
import io.constellationnetwork.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import io.constellationnetwork.json.JsonSerializer
import io.constellationnetwork.routes.internal.ExternalUrlPrefix
import io.constellationnetwork.schema.{GlobalIncrementalSnapshot, GlobalSnapshotInfo, SnapshotOrdinal}
import io.constellationnetwork.security.hash.Hash
import io.constellationnetwork.security.signature.Signed
import io.constellationnetwork.security.{Hashed, SecurityProvider}

import eu.timepit.refined.auto._
import io.circe.{Decoder, Encoder}
import io.constellation.price_oracle.routes.CustomRoutes
import io.constellation.price_oracle.shared_data.calculated_state.CalculatedStateService
import io.constellation.price_oracle.shared_data.combiners.L0CombinerService
import io.constellation.price_oracle.shared_data.storages.GlobalSnapshotsStorage
import io.constellation.price_oracle.shared_data.types.codecs.DataUpdateCodec._
import io.constellation.price_oracle.shared_data.types.codecs.{HasherSelector, JsonWithBase64BinaryCodec}
import io.constellation.price_oracle.shared_data.types.{PriceOracleCalculatedState, PriceOracleOnChainState, PriceUpdate}
import io.constellation.price_oracle.shared_data.validations.ValidationService
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{EntityDecoder, HttpRoutes}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object MetagraphL0Service {
  def make[F[+_]: Async: HasherSelector](
    calculatedStateService: CalculatedStateService[F],
    validationService: ValidationService[F],
    globalSnapshotsStorage: GlobalSnapshotsStorage[F],
    combinerService: L0CombinerService[F],
    jsonSerializer: JsonSerializer[F],
    dataUpdateCodec: JsonWithBase64BinaryCodec[F, PriceUpdate]
  ): BaseDataApplicationL0Service[F] =
    BaseDataApplicationL0Service(new DataApplicationL0Service[F, PriceUpdate, PriceOracleOnChainState, PriceOracleCalculatedState] {
      private def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("MetagraphL0Service")

      def genesis: DataState[PriceOracleOnChainState, PriceOracleCalculatedState] =
        DataState(PriceOracleOnChainState(), PriceOracleCalculatedState())

      def validateData(
        state: DataState[PriceOracleOnChainState, PriceOracleCalculatedState],
        updates: NonEmptyList[Signed[PriceUpdate]]
      )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
        for {
          _ <- logger.info(s"validateData: $updates")
          res <- validationService.validateData(updates, state)
        } yield res

      def combine(state: DataState[PriceOracleOnChainState, PriceOracleCalculatedState], updates: List[Signed[PriceUpdate]])(
        implicit context: L0NodeContext[F]
      ): F[DataState[PriceOracleOnChainState, PriceOracleCalculatedState]] =
        for {
          _ <- logger.info(s"combine: $updates")
          res <- combinerService.combine(state, updates)
        } yield res

      def getCalculatedState(implicit context: L0NodeContext[F]): F[(SnapshotOrdinal, PriceOracleCalculatedState)] =
        for {
          res <- calculatedStateService.get.map(calculatedState => (calculatedState.ordinal, calculatedState.state))
          _ <- logger.info(s"getCalculatedState: $res")
        } yield res

      def setCalculatedState(ordinal: SnapshotOrdinal, state: PriceOracleCalculatedState)(
        implicit context: L0NodeContext[F]
      ): F[Boolean] =
        for {
          lastSyncGlobalEpochProgress <- CalculatedStateService.getEpochProgress(context)
          _ <- logger.info(s"setCalculatedState: $ordinal $state")
          res <- calculatedStateService.update(ordinal, lastSyncGlobalEpochProgress, state)
        } yield res

      def hashCalculatedState(state: PriceOracleCalculatedState)(implicit context: L0NodeContext[F]): F[Hash] =
        calculatedStateService.hash(state)

      def serializeState(state: PriceOracleOnChainState): F[Array[Byte]] = jsonSerializer.serialize[PriceOracleOnChainState](state)

      def deserializeState(bytes: Array[Byte]): F[Either[Throwable, PriceOracleOnChainState]] =
        jsonSerializer.deserialize[PriceOracleOnChainState](bytes)

      def serializeUpdate(update: PriceUpdate): F[Array[Byte]] = dataUpdateCodec.serialize(update)

      def deserializeUpdate(bytes: Array[Byte]): F[Either[Throwable, PriceUpdate]] = dataUpdateCodec.deserialize(bytes)

      def serializeBlock(block: Signed[dataApplication.DataApplicationBlock]): F[Array[Byte]] =
        jsonSerializer.serialize[Signed[DataApplicationBlock]](block)

      def deserializeBlock(bytes: Array[Byte]): F[Either[Throwable, Signed[dataApplication.DataApplicationBlock]]] =
        jsonSerializer.deserialize[Signed[DataApplicationBlock]](bytes)

      def serializeCalculatedState(state: PriceOracleCalculatedState): F[Array[Byte]] =
        jsonSerializer.serialize[PriceOracleCalculatedState](state)

      def deserializeCalculatedState(bytes: Array[Byte]): F[Either[Throwable, PriceOracleCalculatedState]] =
        jsonSerializer.deserialize[PriceOracleCalculatedState](bytes)

      def dataEncoder: Encoder[PriceUpdate] = implicitly[Encoder[PriceUpdate]]

      def dataDecoder: Decoder[PriceUpdate] = implicitly[Decoder[PriceUpdate]]

      def signedDataEntityDecoder: EntityDecoder[F, Signed[PriceUpdate]] = circeEntityDecoder

      def calculatedStateEncoder: Encoder[PriceOracleCalculatedState] = implicitly[Encoder[PriceOracleCalculatedState]]

      def calculatedStateDecoder: Decoder[PriceOracleCalculatedState] = implicitly[Decoder[PriceOracleCalculatedState]]

      def routes(implicit context: L0NodeContext[F]): HttpRoutes[F] = {
        implicit val sp: SecurityProvider[F] = context.securityProvider
        CustomRoutes[F](calculatedStateService, dataUpdateCodec).public
      }

      override def onGlobalSnapshotPull(
        snapshot: Hashed[GlobalIncrementalSnapshot],
        context: GlobalSnapshotInfo
      )(implicit A: Applicative[F]): F[Unit] =
        globalSnapshotsStorage.set(snapshot)

      override def routesPrefix: ExternalUrlPrefix = "v1"
    })

}
