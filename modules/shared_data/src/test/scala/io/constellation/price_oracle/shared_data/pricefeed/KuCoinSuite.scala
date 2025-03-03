package io.constellation.price_oracle.shared_data.pricefeed

import cats.effect.{IO, Resource}

import scala.concurrent.duration.DurationInt

import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import weaver.MutableIOSuite

object KuCoinSuite extends MutableIOSuite {

  override type Res = Client[IO]

  override def sharedResource: Resource[IO, Res] =
    EmberClientBuilder
      .default[IO]
      .withTimeout(10.seconds)
      .build

  test("retrieve non-zero DAG price from KuCoin") { client =>
    val kuCoin = KuCoin.make(client)
    kuCoin.retrievePrice().map(price => expect(price > 0))
  }
}
