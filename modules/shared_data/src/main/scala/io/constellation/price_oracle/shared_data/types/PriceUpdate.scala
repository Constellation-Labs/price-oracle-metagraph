package io.constellation.price_oracle.shared_data.types

import io.constellationnetwork.currency.dataApplication.DataUpdate
import io.constellationnetwork.schema.priceOracle.TokenPair

import derevo.cats.eqv
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

@derive(eqv, encoder, decoder)
case class PriceUpdate(tokenPair: TokenPair, prices: PriceValues, createdAt: Long) extends DataUpdate
