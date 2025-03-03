package io.constellation.price_oracle.shared_data.pricefeed

import cats.effect.Async
import cats.implicits.toFunctorOps

import derevo.cats.{eqv, show}
import derevo.circe.magnolia.decoder
import derevo.derive
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{Method, Request, Uri}

object KuCoin {

  // Data sample:
  //  {
  //    "code": "200000",
  //    "data": [
  //      {
  //        "sequence": "6313810738032641",
  //        "price": "0.044107",
  //        "size": "181.9176",
  //        "side": "sell",
  //        "time": 1740621269331000000
  //      }
  //    ]
  //  }

  @derive(eqv, show, decoder)
  case class Datum(sequence: String, price: String, size: String, side: String, time: Long)
  @derive(eqv, show, decoder)
  case class History(code: String, data: Option[List[Datum]])

  case class InvalidResponseFormat(msg: String) extends RuntimeException(msg)

  def make[F[_]: Async](client: Client[F], symbol: String = "DAG-USDT"): PriceFeed[F] = new PriceFeed[F] with Http4sClientDsl[F] {
    def id: PriceFeedId = PriceFeedId.KuCoin

    def retrievePrice(): F[BigDecimal] = {
      import org.http4s.circe.CirceEntityCodec.circeEntityDecoder

      val request = Request[F](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"https://api.kucoin.com/api/v1/market/histories?symbol=$symbol")
      )

      client.expect[History](request).map { history =>
        val data = history.data.getOrElse(List.empty)
        if (data.isEmpty) {
          throw InvalidResponseFormat("No history data received from KuCoin")
        } else {
          BigDecimal(data.maxBy(_.time).price)
        }
      }
    }
  }
}
