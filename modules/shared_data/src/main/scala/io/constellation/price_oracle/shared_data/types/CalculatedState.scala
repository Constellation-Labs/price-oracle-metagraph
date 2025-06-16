package io.constellation.price_oracle.shared_data.types

import cats.data.NonEmptyList

import io.constellationnetwork.currency.dataApplication.{DataCalculatedState, DataOnChainState, DataUpdate}
import io.constellationnetwork.schema.ID.Id
import io.constellationnetwork.schema.SnapshotOrdinal
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.schema.priceOracle.TokenPair
import io.constellationnetwork.security.signature.Signed

import derevo.cats.eqv
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import eu.timepit.refined.types.numeric.NonNegInt
import io.constellation.price_oracle.shared_data.pricefeed.PriceFeedId

@derive(encoder, decoder)
case class PriceOracleOnChainState(updates: List[PriceUpdate] = List.empty) extends DataOnChainState

@derive(encoder, decoder)
case class PriceValue(priceFeedId: PriceFeedId, price: BigDecimal)

@derive(encoder, decoder)
case class PriceValues(prices: NonEmptyList[PriceValue]) {
  def median: BigDecimal = {
    val arr = prices.toList.toArray.sortBy(_.price)
    if (arr.length % 2 == 0) {
      val i = arr.length / 2 - 1
      val j = arr.length / 2
      (arr(i).price + arr(j).price) / 2
    } else {
      arr(arr.length / 2).price
    }
  }
}

object PriceValues {
  def of(prices: PriceValue*): PriceValues = PriceValues(NonEmptyList.fromListUnsafe(prices.toList))
}

@derive(eqv, encoder, decoder)
case class PriceUpdate(
  tokenPair: TokenPair,
  prices: PriceValues,
  epochProgress: EpochProgress,
  createdAt: Long = System.currentTimeMillis()
) extends DataUpdate

@derive(encoder, decoder)
case class PriceOracleCalculatedState(
  priceState: Map[TokenPair, Map[Id, List[PriceUpdate]]] = Map.empty
) extends DataCalculatedState {
  def combine(that: PriceOracleCalculatedState, maxSize: NonNegInt): PriceOracleCalculatedState =
    (this.priceState.keys ++ that.priceState.keys).toSet.foldLeft(PriceOracleCalculatedState()) { (acc, tokenPair) =>
      val combined = combineTokenPair(
        this.priceState.getOrElse(tokenPair, Map.empty[Id, List[PriceUpdate]]),
        that.priceState.getOrElse(tokenPair, Map.empty[Id, List[PriceUpdate]]),
        maxSize
      )
      acc.copy(priceState = acc.priceState.updated(tokenPair, combined))
    }

  def append(priceUpdate: Signed[PriceUpdate], maxSize: NonNegInt): PriceOracleCalculatedState = {
    val nodeId = priceUpdate.proofs.head.id
    val tokenPair = priceUpdate.tokenPair
    val oldState = priceState.getOrElse(tokenPair, Map.empty[Id, List[PriceUpdate]])
    val oldPrices = oldState.getOrElse(nodeId, List.empty)
    val newPrices = (oldPrices ++ List(priceUpdate.value)).takeRight(maxSize.value)
    copy(priceState = priceState.updated(tokenPair, oldState.updated(nodeId, newPrices)))
  }

  private def combineTokenPair(
    oldState: Map[Id, List[PriceUpdate]],
    newState: Map[Id, List[PriceUpdate]],
    maxSize: NonNegInt
  ): Map[Id, List[PriceUpdate]] =
    (oldState.keys ++ newState.keys).toSet.foldLeft(Map.empty[Id, List[PriceUpdate]]) { (acc, nodeId) =>
      val prices = ((oldState.get(nodeId), newState.get(nodeId)) match {
        case (Some(currPrices), Some(newPrices)) => currPrices ++ newPrices
        case (Some(currPrices), None)            => currPrices
        case (None, Some(newPrices))             => newPrices
        case _                                   => List.empty
      }).takeRight(maxSize.value)
      acc.updated(nodeId, prices)
    }
}

case class CalculatedState(ordinal: SnapshotOrdinal, state: PriceOracleCalculatedState)

object CalculatedState {
  val empty: CalculatedState = CalculatedState(SnapshotOrdinal.MinValue, PriceOracleCalculatedState())
}
