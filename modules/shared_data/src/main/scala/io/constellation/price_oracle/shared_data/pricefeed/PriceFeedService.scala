package io.constellation.price_oracle.shared_data.pricefeed

import cats.data.NonEmptyList
import cats.effect.Async

import io.constellationnetwork.schema.swap.CurrencyId

import io.constellation.price_oracle.shared_data.app.ApplicationConfig
import org.http4s.client.Client

trait PriceFeedService[F[_]] {
  def retrieveAggregatedPrice(currencyId: Option[CurrencyId]): F[BigDecimal]
}

object PriceFeedService {
  def make[F[_]: Async](config: ApplicationConfig, client: Client[F]): PriceFeedService[F] = {
    val priceFeeds: Map[Option[CurrencyId], PriceFeeds[F]] = config.priceFeeds.map { priceFeedConfig =>
      (
        priceFeedConfig.currencyId,
        PriceFeeds.make(NonEmptyList.fromListUnsafe(priceFeedConfig.tickers.map {
          case (PriceFeedId.GateIO, ticker) => GateIO.make(client, ticker)
          case (PriceFeedId.KuCoin, ticker) => KuCoin.make(client, ticker)
          case (PriceFeedId.MEXC, ticker)   => MEXC.make(client, ticker)
        }.toList))
      )
    }.toMap
    new PriceFeedService[F] {
      override def retrieveAggregatedPrice(currencyId: Option[CurrencyId]): F[BigDecimal] = priceFeeds(currencyId).retrieveAggregatedPrice()
    }
  }
}
