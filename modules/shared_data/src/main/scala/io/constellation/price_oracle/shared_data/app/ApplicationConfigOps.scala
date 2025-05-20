package io.constellation.price_oracle.shared_data.app

import cats.effect.kernel.Sync
import cats.syntax.traverse._

import scala.concurrent.duration.DurationInt

import io.constellationnetwork.schema.priceOracle.TokenPair
import io.constellationnetwork.schema.priceOracle.TokenPair.{BTC_USD, DAG_USD, ETH_USD}

import ciris.ConfigValue
import io.constellation.price_oracle.shared_data.app.ApplicationConfig._
import pureconfig._
import pureconfig.error._
import pureconfig.generic.semiauto.deriveReader
import pureconfig.module.catseffect.syntax._

object ApplicationConfigOps {
  import ConfigReaders._

  def readDefault[F[_]: Sync]: F[ApplicationConfig] =
    ConfigSource.default
      .loadF[F, ApplicationConfig]()
}

object ConfigReaders {
  implicit val intervalsConfigReader: ConfigReader[IntervalsConfig] = deriveReader[IntervalsConfig].emap(cfg =>
    Either.cond(
      cfg.poll > 0.seconds && cfg.storage >= cfg.poll,
      cfg,
      CannotConvert(
        cfg.toString,
        "intervals",
        "storage interval must be greater or equal to poll interval and poll interval must be greater than 0"
      )
    )
  )

  implicit val tokenPair: ConfigReader[TokenPair] = ConfigReader.fromString[TokenPair] {
    case "DAG_USD" => Right(DAG_USD)
    case "BTC_USD" => Right(BTC_USD)
    case "ETH_USD" => Right(ETH_USD)
    case other     => Left(CannotConvert(other, "token-pair", "unsupported token pair"))
  }

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

  implicit val applicationConfigReader: ConfigReader[ApplicationConfig] = deriveReader[ApplicationConfig]
}
