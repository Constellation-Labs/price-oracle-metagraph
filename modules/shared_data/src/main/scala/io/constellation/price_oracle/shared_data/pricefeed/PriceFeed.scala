package io.constellation.price_oracle.shared_data.pricefeed

import cats.data.{NonEmptyList, NonEmptySet}
import cats.effect.Async
import cats.syntax.all._

import scala.concurrent.duration._

import derevo.cats.{eqv, order, show}
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.constellation.price_oracle.shared_data.types.{PriceValue, PriceValues}
import org.http4s.client.Client
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import retry.RetryPolicies._
import retry.syntax.all._

@derive(eqv, show, encoder, decoder, order)
sealed trait PriceFeedId
object PriceFeedId {
  case object GateIO extends PriceFeedId

  case object KuCoin extends PriceFeedId

  case object MEXC extends PriceFeedId
}

trait PriceFeed[F[_]] {
  def id: PriceFeedId
  def retrievePrice(): F[BigDecimal]
}

trait PriceFeeds[F[_]] {
  def retrievePrices(): F[PriceValues]
}

object PriceFeeds {

  case class NoPriceData(msg: String) extends RuntimeException(msg)

  def make[F[_]: Async](priceFeeds: NonEmptyList[PriceFeed[F]], retryDelay: FiniteDuration = 1.second, numRetries: Int = 9): PriceFeeds[F] =
    new PriceFeeds[F] {
      private def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

      def retrievePrices(): F[PriceValues] =
        (for {
          prices <- priceFeeds.toList
            .traverse(feed => feed.retrievePrice().attempt.map(res => res.toOption.map(p => PriceValue(feed.id, p))))
            .map(_.flatten)
          nel <-
            if (prices.isEmpty)
              NoPriceData(s"All price feeds have failed").raiseError[F, NonEmptyList[PriceValue]]
            else
              NonEmptyList.fromList(prices).liftTo[F](NoPriceData(s"All price feeds have failed"))
        } yield PriceValues(nel)).retryingOnAllErrors(
          policy = fibonacciBackoff(retryDelay).join(limitRetries(numRetries)),
          onError = (error, retryDetails) =>
            Async[F].defer(logger.error(s"Failed to retrieve price, attempt ${retryDetails.retriesSoFar + 1}: ${error.getMessage}"))
        )
    }

  def createPriceFeeds[F[_]: Async](client: Client[F], priceFeedIds: NonEmptySet[PriceFeedId]): NonEmptyList[PriceFeed[F]] =
    priceFeedIds.toNonEmptyList.map(createPriceFeed(client, _))

  def createPriceFeed[F[_]: Async](client: Client[F], priceFeedId: PriceFeedId): PriceFeed[F] =
    priceFeedId match {
      case PriceFeedId.GateIO => GateIO.make(client)
      case PriceFeedId.KuCoin => KuCoin.make(client)
      case PriceFeedId.MEXC   => MEXC.make(client)
    }
}
