package io.constellation.price_oracle.shared_data.pricefeed

import cats.effect.Async
import cats.implicits.{catsSyntaxApplicativeError, toFunctorOps}

import derevo.cats.{eqv, show}
import derevo.circe.magnolia.decoder
import derevo.derive
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{Method, Request, Uri}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object MEXC {

  // Data sample:
  //  [
  //    {
  //      "id": null,
  //      "price": "0.0443",
  //      "qty": "185.81",
  //      "quoteQty": "8.231383",
  //      "time": 1740623630814,
  //      "isBuyerMaker": true,
  //      "isBestMatch": true,
  //      "tradeType": "ASK"
  //    }
  //  ]

  @derive(eqv, show, decoder)
  case class Trade(
    id: Option[String],
    price: String,
    qty: String,
    quoteQty: String,
    time: Long,
    isBuyerMaker: Boolean,
    isBestMatch: Boolean,
    tradeType: String
  )

  case class InvalidResponseFormat(msg: String) extends RuntimeException(msg)

  def make[F[_]: Async](client: Client[F], symbol: String = "DAGUSDT"): PriceFeed[F] = new PriceFeed[F] with Http4sClientDsl[F] {
    def id: PriceFeedId = PriceFeedId.MEXC

    def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("MEXC")

    def retrievePrice(): F[BigDecimal] = {
      import org.http4s.circe.CirceEntityCodec.circeEntityDecoder

      val request = Request[F](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"https://api.mexc.com/api/v3/trades?symbol=$symbol&limit=1")
      )

      logger.debug(s"requesting $request")

      client
        .expect[List[Trade]](request)
        .map { trades =>
          if (trades.isEmpty) {
            throw InvalidResponseFormat("No history data received from MEXC")
          } else {
            BigDecimal(trades.maxBy(_.time).price)
          }
        }
        .onError {
          case t: Throwable =>
            logger.error(t)(s"Failed to get price from GateIO: ${t.getMessage}")
        }
    }
  }
}
