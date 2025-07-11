package io.constellation.price_oracle.data_l1

import java.util.UUID

import cats.effect.{IO, Resource}
import cats.syntax.option._

import io.constellationnetwork.BuildInfo
import io.constellationnetwork.currency.dataApplication.BaseDataApplicationL1Service
import io.constellationnetwork.currency.l1.CurrencyL1App
import io.constellationnetwork.ext.cats.effect.ResourceIO
import io.constellationnetwork.json.{JsonSerializer => JsonBrotliBinaryCodec}
import io.constellationnetwork.schema.cluster.ClusterId
import io.constellationnetwork.schema.semver.{MetagraphVersion, TessellationVersion}
import io.constellationnetwork.security.signature.SignedValidator
import io.constellationnetwork.security.{Hasher, SecurityProvider}

import io.constellation.price_oracle.shared_data.app.ApplicationConfigOps
import io.constellation.price_oracle.shared_data.types.codecs.{HasherSelector, JsonBinaryCodec}
import io.constellation.price_oracle.shared_data.validations.ValidationService

object Main
    extends CurrencyL1App(
      "price-oracle-data_l1",
      "Price oracle data L1 data node",
      ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
      tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version),
      metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version)
    ) {

  override def dataApplication: Option[Resource[IO, BaseDataApplicationL1Service[IO]]] = (for {
    config <- ApplicationConfigOps.readDefault[IO].flatTap(config => logger.info(config.toString)).asResource
    implicit0(sp: SecurityProvider[IO]) <- SecurityProvider.forAsync[IO]
    jsonBinaryCodec <- JsonBinaryCodec.forSync[IO].asResource
    implicit0(hasherCurrent: Hasher[IO]) = {
      implicit val serializer: JsonBrotliBinaryCodec[IO] = jsonBinaryCodec
      Hasher.forJson[IO]
    }
    validationService = ValidationService.make[IO](config)
    l1Service = DataL1Service.make[IO](validationService, jsonBinaryCodec)
  } yield l1Service).some
}
