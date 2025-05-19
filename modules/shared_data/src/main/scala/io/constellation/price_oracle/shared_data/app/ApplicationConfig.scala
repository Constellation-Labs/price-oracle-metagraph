package io.constellation.price_oracle.shared_data.app

import cats.data.NonEmptyList
import cats.effect.Async
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxPartialOrder}

import scala.concurrent.duration.FiniteDuration

import io.constellationnetwork.schema.ID.Id
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.schema.priceOracle.TokenPair

import ciris.Secret
import eu.timepit.refined.types.numeric.{NonNegInt, NonNegLong}
import io.constellation.price_oracle.shared_data.app.ApplicationConfig.{IntervalsConfig, PriceFeedConfig, SigningConfig}
import io.constellation.price_oracle.shared_data.pricefeed.PriceFeedId
import io.constellation.price_oracle.shared_data.pricefeed.PriceFeedId.{GateIO, KuCoin, MEXC}
import org.http4s.Uri

case class ApplicationConfig(
  dataL1ServerUri: Uri,
  environment: ApplicationConfig.Environment,
  intervals: NonEmptyList[IntervalsConfig],
  seedlist: List[Id],
  signing: SigningConfig,
  priceFeeds: NonEmptyList[PriceFeedConfig]
) {
  def intervalsConfig[F[_]: Async](currentEpoch: EpochProgress): F[IntervalsConfig] =
    intervals.sortBy(_.asOfEpoch).reverse.find(_.asOfEpoch <= currentEpoch) match {
      case Some(cfg) => cfg.pure[F]
      case None      => Async[F].raiseError(new Exception(s"Cannot find an interval config for ${currentEpoch}"))
    }
}

object ApplicationConfig {

  sealed trait Environment
  case object Dev extends Environment
  case object Testnet extends Environment
  case object Integrationnet extends Environment
  case object Mainnet extends Environment

  case class IntervalsConfig(
    poll: FiniteDuration,
    storage: FiniteDuration,
    movingAverage: FiniteDuration,
    minEpochsBetweenUpdates: NonNegLong,
    asOfEpoch: EpochProgress
  ) {
    val maxSize: NonNegInt = NonNegInt.unsafeFrom((storage / poll).toInt)
    val movingAverageWindowSize: NonNegInt = NonNegInt.unsafeFrom((movingAverage / poll).toInt)
  }

  case class PriceFeedConfig(
    tokenPair: TokenPair,
    gateioTicker: Option[String],
    kucoinTicker: Option[String],
    mexcTicker: Option[String]
  ) {
    def tickers: Map[PriceFeedId, String] = List(
      gateioTicker.map(ticker => (GateIO, ticker)),
      kucoinTicker.map(ticker => (KuCoin, ticker)),
      mexcTicker.map(ticker => (MEXC, ticker))
    ).flatten.toMap
  }

  case class SigningConfig(keyPairStore: String, alias: Secret[String], password: Secret[String])
}
