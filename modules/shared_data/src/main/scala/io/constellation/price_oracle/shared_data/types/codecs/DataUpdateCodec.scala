package io.constellation.price_oracle.shared_data.types.codecs

import io.constellationnetwork.currency.dataApplication.DataUpdate

import io.circe._
import io.circe.syntax.EncoderOps
import io.constellation.price_oracle.shared_data.types.PriceUpdate

object DataUpdateCodec {
  implicit val dataUpdateEncoder: Encoder[DataUpdate] = {
    case event: PriceUpdate => event.asJson
    case _                  => Json.Null
  }

  implicit val dataUpdateDecoder: Decoder[DataUpdate] = (c: HCursor) => c.as[PriceUpdate]
}
