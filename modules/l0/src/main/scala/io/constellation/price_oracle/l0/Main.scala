package io.constellation.price_oracle.l0

import java.util.UUID

import cats.effect.std.Supervisor
import cats.effect.{IO, Resource}
import cats.syntax.option._

import scala.concurrent.duration.DurationInt

import io.constellationnetwork.BuildInfo
import io.constellationnetwork.currency.dataApplication.BaseDataApplicationL0Service
import io.constellationnetwork.currency.l0.CurrencyL0App
import io.constellationnetwork.ext.cats.effect.ResourceIO
import io.constellationnetwork.json.{JsonSerializer => JsonBrotliBinaryCodec}
import io.constellationnetwork.keytool.KeyStoreUtils
import io.constellationnetwork.node.shared.config.types.HttpClientConfig
import io.constellationnetwork.node.shared.resources.MkHttpClient
import io.constellationnetwork.schema.cluster.ClusterId
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.schema.semver.{MetagraphVersion, TessellationVersion}
import io.constellationnetwork.security.signature.SignedValidator
import io.constellationnetwork.security.{Hasher, SecurityProvider}

import io.constellation.price_oracle.shared_data.app.ApplicationConfigOps
import io.constellation.price_oracle.shared_data.calculated_state.CalculatedStateService
import io.constellation.price_oracle.shared_data.combiners.L0CombinerService
import io.constellation.price_oracle.shared_data.pricefeed.{PriceFeedDaemon, PriceFeedService, PriceUpdateFunction}
import io.constellation.price_oracle.shared_data.storages.GlobalSnapshotsStorage
import io.constellation.price_oracle.shared_data.types.PriceUpdate
import io.constellation.price_oracle.shared_data.types.codecs.{HasherSelector, JsonBinaryCodec, JsonWithBase64BinaryCodec}
import io.constellation.price_oracle.shared_data.validations.ValidationService

object Main
    extends CurrencyL0App(
      "price-oracle-l0",
      "Price oracle L0 node",
      ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
      tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version),
      metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version)
    ) {

  override def dataApplication: Option[Resource[IO, BaseDataApplicationL0Service[IO]]] = (for {
    implicit0(sp: SecurityProvider[IO]) <- SecurityProvider.forAsync[IO]
    implicit0(supervisor: Supervisor[IO]) <- Supervisor[IO]
    jsonBrotliBinaryCodec <- JsonBrotliBinaryCodec.forSync[IO].asResource
    jsonBase64BinaryCodec <- JsonWithBase64BinaryCodec.forSync[IO, PriceUpdate].asResource
    jsonBinaryCodec <- JsonBinaryCodec.forSync[IO].asResource
    hasherBrotli = {
      implicit val serializer: JsonBrotliBinaryCodec[IO] = jsonBrotliBinaryCodec
      Hasher.forJson[IO]
    }
    implicit0(hasherCurrent: Hasher[IO]) = {
      implicit val serializer: JsonBrotliBinaryCodec[IO] = jsonBinaryCodec
      Hasher.forJson[IO]
    }
    implicit0(hasherSelector: HasherSelector[IO]) = HasherSelector.forSync(hasherBrotli, hasherCurrent)
    config <- ApplicationConfigOps.readDefault[IO].flatTap(config => logger.info(config.toString)).asResource
    _ = println(config)
    calculatedStateService <- CalculatedStateService.make[IO](config).asResource
    globalSnapshotsStorage: GlobalSnapshotsStorage[IO] <- GlobalSnapshotsStorage.make[IO].asResource
    validationService = ValidationService.make[IO](config)
    combinerService = L0CombinerService.make[IO](config)

    l0Service = MetagraphL0Service
      .make[IO](
        calculatedStateService,
        validationService,
        globalSnapshotsStorage,
        combinerService,
        jsonBinaryCodec,
        jsonBase64BinaryCodec
      )

    httpConfig = HttpClientConfig(timeout = 30.seconds, idleTimeInPool = 60.seconds)

    keyPair <- KeyStoreUtils
      .readKeyPairFromStore[IO](
        config.signing.keyPairStore,
        config.signing.alias.value,
        config.signing.password.value.toCharArray,
        config.signing.password.value.toCharArray
      )
      .onError {
        case t: Throwable => logger.error(t)("cannot load signing key")
      }
      .attempt
      .asResource

    latestSnapshot <- globalSnapshotsStorage.get.asResource
    latestSnapshotEpochProgress = latestSnapshot.map(_.epochProgress).getOrElse(EpochProgress.MinValue)
    intervalsConfig <- config.intervalsConfig[IO](latestSnapshotEpochProgress).asResource

    _ <- MkHttpClient[IO]
      .newEmber(httpConfig)
      .use { httpClient =>
        val priceUpdateFunction = PriceUpdateFunction.make[IO](config, keyPair.toOption, httpClient, globalSnapshotsStorage)
        val priceFeedService = PriceFeedService.make[IO](config, httpClient, priceUpdateFunction)
        logger.info("Starting price feed daemon")

        PriceFeedDaemon.make[IO](priceFeedService, intervalsConfig).start.void
      }
      .asResource

  } yield l0Service).some

}
