package io.constellation.price_oracle.shared_data.pricefeed

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._

import io.constellation.price_oracle.shared_data.app.ApplicationConfig
import org.http4s.client.Client
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait PriceFeedService[F[_]] {
  def retrievePrices(): F[Unit]
}

object PriceFeedService {
  def make[F[_]: Async](config: ApplicationConfig, client: Client[F], priceUpdateFunction: PriceUpdateFunction[F]): PriceFeedService[F] = {
    val priceFeeds = config.priceFeeds.map { priceFeedConfig =>
      (
        priceFeedConfig.tokenPair,
        PriceFeeds.make(NonEmptyList.fromListUnsafe(priceFeedConfig.tickers.map {
          case (PriceFeedId.GateIO, ticker) => GateIO.make(client, ticker)
          case (PriceFeedId.KuCoin, ticker) => KuCoin.make(client, ticker)
          case (PriceFeedId.MEXC, ticker)   => MEXC.make(client, ticker)
        }.toList))
      )
    }
    new PriceFeedService[F] {
      private def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("PriceFeedService")
      override def retrievePrices(): F[Unit] = priceFeeds.traverse {
        case (tokenPair, priceFeed) =>
          logger.info(s"retrieving prices for $tokenPair").flatMap { _ =>
            priceFeed
              .retrievePrices()
              .flatMap(prices => priceUpdateFunction(tokenPair, prices))
          }
      }.void
    }
  }
}
