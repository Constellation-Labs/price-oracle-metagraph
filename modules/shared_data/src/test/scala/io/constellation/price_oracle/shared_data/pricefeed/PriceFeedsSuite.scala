package io.constellation.price_oracle.shared_data.pricefeed

import cats.data.{NonEmptyList, NonEmptySet}
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.catsSyntaxEq

import scala.concurrent.duration.DurationInt

import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import weaver.MutableIOSuite

object PriceFeedsSuite extends MutableIOSuite {

  override type Res = Client[IO]

  override def sharedResource: Resource[IO, Res] =
    EmberClientBuilder
      .default[IO]
      .withTimeout(10.seconds)
      .build

  test("should compute median value") { _ =>
    IO(
      expect.all(
        PriceFeeds.median(prices(1)) === BigDecimal("1"),
        PriceFeeds.median(prices(1, 2)) === BigDecimal("1.5"),
        PriceFeeds.median(prices(1, 2, 3)) === BigDecimal("2")
      )
    )
  }

  test("should retrieve non-zero DAG price from 3 price feeds") { client =>
    val feeds = PriceFeeds.createPriceFeeds(client, NonEmptySet.of(PriceFeedId.GateIO, PriceFeedId.KuCoin, PriceFeedId.MEXC))
    val priceFeeds = PriceFeeds.make(feeds, numRetries = 0)
    for {
      prices <- priceFeeds.retrievePrices()
    } yield
      expect.all(
        prices.size === 3,
        prices.forall(_ > 0)
      )
  }

  test("should fail gracefully when one of the price feeds does not respond") { client =>
    val feeds = NonEmptyList.of(
      GateIO.make(client, "DAG#USDT"),
      KuCoin.make(client),
      MEXC.make(client)
    )

    val priceFeeds = PriceFeeds.make(feeds, numRetries = 0)
    for {
      prices <- priceFeeds.retrievePrices()
    } yield
      expect.all(
        prices.size === 2,
        prices.forall(_ > 0)
      )
  }

  test("should fail with NoPriceData when all the price feeds do not respond") { client =>
    val feeds = NonEmptyList.of(
      GateIO.make(client, "DAG#USDT"),
      KuCoin.make(client, "DAG#USDT"),
      MEXC.make(client, "DAG#USDT")
    )

    val priceFeeds = PriceFeeds.make(feeds, numRetries = 0)

    val prices = priceFeeds.retrievePrices()

    prices.attempt.map { either =>
      expect(either == Left(PriceFeeds.NoPriceData("All price feeds have failed")))
    }
  }

  private def prices(numbers: Int*): NonEmptyList[BigDecimal] = NonEmptyList.fromListUnsafe(numbers.map(BigDecimal(_)).toList)
}
