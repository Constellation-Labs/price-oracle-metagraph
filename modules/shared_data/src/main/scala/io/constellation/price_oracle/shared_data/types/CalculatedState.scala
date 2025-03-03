package io.constellation.price_oracle.shared_data.types

import io.constellationnetwork.currency.dataApplication.{DataCalculatedState, DataOnChainState}
import io.constellationnetwork.schema.SnapshotOrdinal
import io.constellationnetwork.schema.priceOracle.PriceRecord
import io.constellationnetwork.schema.swap.CurrencyId

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

@derive(encoder, decoder)
case class PriceOracleOnChainState(updates: List[PriceUpdate]) extends DataOnChainState

@derive(encoder, decoder)
case class PriceOracleCalculatedState(priceState: Map[CurrencyId, PriceRecord] = Map.empty) extends DataCalculatedState {
  def combine(that: PriceOracleCalculatedState): PriceOracleCalculatedState = PriceOracleCalculatedState(this.priceState ++ that.priceState)
}

case class CalculatedState(ordinal: SnapshotOrdinal, state: PriceOracleCalculatedState)

object CalculatedState {
  val empty = CalculatedState(SnapshotOrdinal.MinValue, PriceOracleCalculatedState())
}
