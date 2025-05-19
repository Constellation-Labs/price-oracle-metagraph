package io.constellation.price_oracle.shared_data.app

import cats.data.NonEmptyList
import cats.effect.kernel.Sync

import scala.concurrent.duration.DurationInt
import scala.util.Try

import io.constellationnetwork.schema.ID.Id
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.schema.priceOracle.TokenPair
import io.constellationnetwork.schema.priceOracle.TokenPair.DAG_USD
import io.constellationnetwork.security.hex.Hex

import ciris.Secret
import com.comcast.ip4s.{Host, Port}
import eu.timepit.refined.types.numeric.NonNegLong
import io.constellation.price_oracle.shared_data.app.ApplicationConfig._
import org.http4s.Uri
import pureconfig._
import pureconfig.error._
import pureconfig.generic.semiauto._
import pureconfig.module.catseffect.syntax._

object ApplicationConfigOps {
  import ConfigReaders._

  def readDefault[F[_]: Sync]: F[ApplicationConfig] =
    ConfigSource.default
      .loadF[F, ApplicationConfig]()
}

object ConfigReaders {
  implicit def nonEmptyListReader[A: ConfigReader]: ConfigReader[NonEmptyList[A]] =
    ConfigReader[List[A]].emap(list =>
      Either.cond(
        list.nonEmpty,
        NonEmptyList.fromListUnsafe(list),
        CannotConvert("", "list", "List cannot be empty")
      )
    )

  implicit val nonNegLongReader: ConfigReader[NonNegLong] = ConfigReader.fromString[NonNegLong](str =>
    Try(NonNegLong.unsafeFrom(str.toLong)).toOption.toRight(CannotConvert(str, "NonNegLong", "Invalid non negative long"))
  )

  implicit val epochProgressReader: ConfigReader[EpochProgress] = ConfigReader.fromString[EpochProgress] { str =>
    Try(EpochProgress.apply(NonNegLong.unsafeFrom(str.toLong))).toOption
      .toRight(CannotConvert(str, "EpochProgress", "Invalid epoch progress format"))
  }

  implicit val hostReader: ConfigReader[Host] = ConfigReader.fromString[Host] { str =>
    Host.fromString(str).toRight(CannotConvert(str, "Host", "Invalid host format"))
  }

  implicit val portReader: ConfigReader[Port] = ConfigReader.fromString[Port] { str =>
    Port.fromString(str).toRight(CannotConvert(str, "Port", "Invalid port format"))
  }

  implicit val idReader: ConfigReader[Id] = ConfigReader.fromString[Id](str => Right(Id(Hex(str))))

  implicit val secretStringReader: ConfigReader[Secret[String]] = ConfigReader.fromString[Secret[String]](s => Right(Secret(s)))

  implicit val intervalsConfigReader: ConfigReader[IntervalsConfig] = deriveReader[IntervalsConfig].emap(cfg =>
    Either.cond(
      cfg.poll > 0.seconds && cfg.storage >= cfg.poll && cfg.movingAverage <= cfg.storage,
      cfg,
      CannotConvert(
        cfg.toString,
        "intervals",
        "storage interval must be greater or equal to poll interval and poll interval must be greater than 0"
      )
    )
  )

  implicit val tokenPair: ConfigReader[TokenPair] = ConfigReader.fromString[TokenPair] {
    case "DAG::USD" => Right(DAG_USD)
    case other      => Left(CannotConvert(other, "token-pair", "unsupported token pair"))
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

  implicit val uriReader: ConfigReader[Uri] = ConfigReader.fromString[Uri](s =>
    Uri.fromString(s) match {
      case Right(uri) => Right(uri)
      case Left(err)  => Left(CannotConvert(s, "Uri", s"Invalid URI format: ${err.message}"))
    }
  )

  implicit val signingConfigReader: ConfigReader[SigningConfig] = deriveReader[SigningConfig]

  implicit val applicationConfigReader: ConfigReader[ApplicationConfig] =
    deriveReader[ApplicationConfig]
}
