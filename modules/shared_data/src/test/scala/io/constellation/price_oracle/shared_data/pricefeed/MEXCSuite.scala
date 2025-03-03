package io.constellation.price_oracle.shared_data.pricefeed

import cats.effect.{IO, Resource}

import scala.concurrent.duration.DurationInt

import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import weaver.MutableIOSuite

object MEXCSuite extends MutableIOSuite {

  override type Res = Client[IO]

  override def sharedResource: Resource[IO, Res] =
    EmberClientBuilder
      .default[IO]
      .withTimeout(10.seconds)
      .build

  test("retrieve non-zero DAG price from MEXC") { client =>
    val mexc = MEXC.make(client)
    mexc.retrievePrice().map(price => expect(price > 0))
  }
}
