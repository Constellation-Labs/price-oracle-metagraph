package io.constellation.price_oracle.shared_data.pricefeed

import cats.effect.IO
import cats.effect.kernel.Resource

import scala.concurrent.duration.DurationInt

import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import weaver.MutableIOSuite

object GateIOSuite extends MutableIOSuite {

  override type Res = Client[IO]

  override def sharedResource: Resource[IO, Res] =
    EmberClientBuilder
      .default[IO]
      .withTimeout(10.seconds)
      .build

  test("retrieve non-zero DAG price from GateIO") { client =>
    val gateIO = GateIO.make(client)
    gateIO.retrievePrice().map(price => expect(price > 0))
  }
}
