package io.constellation.price_oracle.shared_data.pricefeed

import java.time.Instant

import cats.effect.Async
import cats.syntax.all._

import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{Method, Request, Uri}

object GateIO {
  // Data sample:
  // ["1740544200","12.93508600","0.04444","0.04456","0.04442","0.04456","290.99000000","true"]
  type Candlestick = (String, String, String, String, String, String, String, String)

  case class InvalidResponseFormat(msg: String) extends RuntimeException(msg)

  def make[F[_]: Async](client: Client[F], symbol: String = "DAG_USDT"): PriceFeed[F] = new PriceFeed[F] with Http4sClientDsl[F] {
    val id: PriceFeedId = PriceFeedId.GateIO

    def retrievePrice(): F[BigDecimal] = {
      import org.http4s.circe.CirceEntityCodec.circeEntityDecoder

      val now = Instant.now()

      val request = Request[F](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"https://api.gateio.ws/api/v4/spot/candlesticks?currency_pair=$symbol&from=${now.getEpochSecond}")
      )

      client
        .expect[List[Candlestick]](request)
        .map { candlesticks =>
          if (candlesticks.isEmpty) {
            throw InvalidResponseFormat("No candlesticks data received from GateIO")
          } else {
            // Assuming the first candlestick's close price is what we want
            // and that the close price is in the 3rd position
            BigDecimal(candlesticks.maxBy(_._1.toLong)._3)
          }
        }
    }
  }

}
