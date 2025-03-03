package io.constellation.price_oracle.shared_data.app

import cats.effect.kernel.Sync
import cats.syntax.option._

import io.constellationnetwork.ext.http4s.AddressVar
import io.constellationnetwork.schema.swap.CurrencyId

import io.constellation.price_oracle.shared_data.app.ApplicationConfig._
import pureconfig._
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto.deriveReader
import pureconfig.module.catseffect.syntax._

object ApplicationConfigOps {
  import ConfigReaders._

  def readDefault[F[_]: Sync]: F[ApplicationConfig] =
    ConfigSource.default
      .loadF[F, ApplicationConfig]()
}

object ConfigReaders {
  implicit val currencyIdReader: ConfigReader[CurrencyId] = ConfigReader[String].map(s => AddressVar.unapply(s).map(CurrencyId(_)).get)

  implicit val priceFeedConfigReader: ConfigReader[PriceFeedConfig] = deriveReader[PriceFeedConfig].emap(cfg =>
    Either.cond(
      List(cfg.gateioTicker, cfg.kucoinTicker, cfg.mexcTicker).flatten.nonEmpty,
      cfg,
      CannotConvert(cfg.toString, "price-feed", "all tickers are empty")
    )
  )

  implicit val environmentReader: ConfigReader[Environment] = ConfigReader.fromString[Environment] {
    case "dev"            => Right(Dev)
    case "testnet"        => Right(Testnet)
    case "integrationnet" => Right(Integrationnet)
    case "mainnet"        => Right(Mainnet)
    case other            => Left(CannotConvert(other, "Environment", "Must be 'dev', 'testnet', 'integrationnet', or 'mainnet'"))
  }

  implicit val applicationConfigReader: ConfigReader[ApplicationConfig] = deriveReader
}
