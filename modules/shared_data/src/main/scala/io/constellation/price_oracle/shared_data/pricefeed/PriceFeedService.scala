package io.constellation.price_oracle.shared_data.pricefeed

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._

import io.constellation.price_oracle.shared_data.app.ApplicationConfig
import org.http4s.client.Client

trait PriceFeedService[F[_]] {
  def updatePrices(): F[Unit]
}

object PriceFeedService {
  def make[F[_]: Async](config: ApplicationConfig, client: Client[F], priceUpdateFunction: PriceUpdateFunction[F]): PriceFeedService[F] = {
    val priceFeeds = config.priceFeeds.map { priceFeedConfig =>
      (
        priceFeedConfig.currencyId,
        PriceFeeds.make(NonEmptyList.fromListUnsafe(priceFeedConfig.tickers.map {
          case (PriceFeedId.GateIO, ticker) => GateIO.make(client, ticker)
          case (PriceFeedId.KuCoin, ticker) => KuCoin.make(client, ticker)
          case (PriceFeedId.MEXC, ticker)   => MEXC.make(client, ticker)
        }.toList))
      )
    }
    new PriceFeedService[F] {
      override def updatePrices(): F[Unit] = priceFeeds.traverse {
        case (currencyId, priceFeed) =>
          priceFeed
            .retrieveAggregatedPrice()
            .flatMap(price => priceUpdateFunction(currencyId, price))
      }.void
    }
  }
}
