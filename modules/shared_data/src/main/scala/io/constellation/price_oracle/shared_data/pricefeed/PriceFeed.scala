package io.constellation.price_oracle.shared_data.pricefeed

import cats.data.{NonEmptyList, NonEmptySet}
import cats.effect.Async
import cats.kernel.Order
import cats.syntax.all._

import scala.concurrent.duration._

import derevo.cats.{eqv, order, show}
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fs2.Stream
import org.http4s.client.Client
import retry.RetryPolicies._
import retry._
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
  def retrieveAggregatedPrice(): F[BigDecimal]
  def retrievePrices(): F[NonEmptyList[BigDecimal]]
}

object PriceFeeds {

  case class NoPriceData(msg: String) extends RuntimeException(msg)

  def make[F[_]: Async](priceFeeds: NonEmptyList[PriceFeed[F]], retryDelay: FiniteDuration = 1.second, numRetries: Int = 9): PriceFeeds[F] =
    new PriceFeeds[F] {

      def retrievePrices(): F[NonEmptyList[BigDecimal]] =
        (for {
          prices <- priceFeeds.toList.traverse(feed => feed.retrievePrice().attempt.map(_.toOption)).map(_.flatten)
          nel <-
            if (prices.isEmpty)
              NoPriceData(s"All price feeds have failed").raiseError[F, NonEmptyList[BigDecimal]]
            else
              NonEmptyList.fromList(prices).liftTo[F](NoPriceData(s"All price feeds have failed"))
        } yield nel).retryingOnAllErrors(
          policy = fibonacciBackoff(retryDelay).join(limitRetries(numRetries)),
          onError = (error, retryDetails) =>
            Async[F].delay(println(s"Failed to retrieve price, attempt ${retryDetails.retriesSoFar + 1}: ${error.getMessage}"))
        )

      def retrieveAggregatedPrice(): F[BigDecimal] = retrievePrices().map(median)
    }

  def createPriceFeeds[F[_]: Async](client: Client[F], priceFeedIds: NonEmptySet[PriceFeedId]): NonEmptyList[PriceFeed[F]] =
    priceFeedIds.toNonEmptyList.map(createPriceFeed(client, _))

  def createPriceFeed[F[_]: Async](client: Client[F], priceFeedId: PriceFeedId): PriceFeed[F] =
    priceFeedId match {
      case PriceFeedId.GateIO => GateIO.make(client)
      case PriceFeedId.KuCoin => KuCoin.make(client)
      case PriceFeedId.MEXC   => MEXC.make(client)
    }

  def median(prices: NonEmptyList[BigDecimal]): BigDecimal = {
    val arr = prices.toList.toArray.sorted
    if (arr.length % 2 == 0) {
      val i = arr.length / 2 - 1
      val j = arr.length / 2
      (arr(i) + arr(j)) / 2
    } else {
      arr(arr.length / 2)
    }
  }
}
