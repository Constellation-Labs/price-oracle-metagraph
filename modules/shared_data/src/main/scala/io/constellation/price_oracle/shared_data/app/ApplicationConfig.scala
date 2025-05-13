package io.constellation.price_oracle.shared_data.app

import scala.concurrent.duration.FiniteDuration

import io.constellationnetwork.schema.swap.CurrencyId

import io.constellation.price_oracle.shared_data.pricefeed.PriceFeedId
import io.constellation.price_oracle.shared_data.pricefeed.PriceFeedId.{GateIO, KuCoin, MEXC}

case class ApplicationConfig(
  environment: ApplicationConfig.Environment,
  priceFeeds: List[ApplicationConfig.PriceFeedConfig],
  pollInterval: FiniteDuration
)

object ApplicationConfig {

  sealed trait Environment
  case object Dev extends Environment
  case object Testnet extends Environment
  case object Integrationnet extends Environment
  case object Mainnet extends Environment

  case class PriceFeedConfig(
    currencyId: Option[CurrencyId],
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
}
