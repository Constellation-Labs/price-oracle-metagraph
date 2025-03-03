package io.constellation.price_oracle.shared_data.types

import io.constellationnetwork.currency.dataApplication.DataUpdate
import io.constellationnetwork.schema.swap.CurrencyId

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import eu.timepit.refined.types.numeric.NonNegLong
//import eu.timepit.refined.auto.autoRefineV
//import eu.timepit.refined.cats._
//import eu.timepit.refined.types.numeric.NonNegLong

@derive(encoder, decoder)
case class PriceUpdate(currencyId: CurrencyId, price: BigDecimal, timestamp: Long) extends DataUpdate
