package io.constellation.price_oracle.shared_data.types

import cats.data.NonEmptyList
import cats.syntax.option._

import io.constellationnetwork.currency.dataApplication.{DataCalculatedState, DataOnChainState}
import io.constellationnetwork.schema.SnapshotOrdinal
import io.constellationnetwork.schema.swap.CurrencyId

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import eu.timepit.refined.types.numeric.NonNegInt
import io.circe.{KeyDecoder, KeyEncoder}
import io.constellation.price_oracle.shared_data.pricefeed.PriceFeedId

@derive(encoder, decoder)
case class PriceOracleOnChainState(updates: List[PriceUpdate]) extends DataOnChainState

@derive(encoder, decoder)
case class PriceValue(priceFeedId: PriceFeedId, price: BigDecimal)

@derive(encoder, decoder)
case class PriceRecord(prices: NonEmptyList[PriceValue]) {
  def median(): BigDecimal = {
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

@derive(encoder, decoder)
case class PriceOracleCalculatedState(priceState: Map[Option[CurrencyId], NonEmptyList[PriceRecord]] = Map.empty)
    extends DataCalculatedState {
  def combine(that: PriceOracleCalculatedState, maxSize: NonNegInt): PriceOracleCalculatedState =
    (this.priceState.keys ++ that.priceState.keys).toSet.foldLeft(PriceOracleCalculatedState()) { (acc, currencyId) =>
      val prices = NonEmptyList.fromListUnsafe(((this.priceState.get(currencyId), that.priceState.get(currencyId)) match {
        case (Some(currPrices), Some(newPrices)) => currPrices.toList ++ newPrices.toList
        case (Some(currPrices), None)            => currPrices.toList
        case (None, Some(newPrices))             => newPrices.toList
        case _                                   => List.empty
      }).takeRight(maxSize.value))
      acc.copy(priceState = acc.priceState.updated(currencyId, prices))
    }
}

object PriceOracleCalculatedState {
  implicit val optionCurrencyIdKeyEncoder: KeyEncoder[Option[CurrencyId]] = new KeyEncoder[Option[CurrencyId]] {
    override def apply(key: Option[CurrencyId]): String = key match {
      case None             => KeyEncoder[String].apply("")
      case Some(currencyId) => KeyEncoder[CurrencyId].apply(currencyId)
    }
  }

  implicit val optionCurrencyIdKeyDecoder: KeyDecoder[Option[CurrencyId]] = new KeyDecoder[Option[CurrencyId]] {
    override def apply(key: String): Option[Option[CurrencyId]] =
      if (key == "") Some(None) else KeyDecoder[CurrencyId].apply(key).map(_.some)
  }
}

case class CalculatedState(ordinal: SnapshotOrdinal, state: PriceOracleCalculatedState)

object CalculatedState {
  val empty = CalculatedState(SnapshotOrdinal.MinValue, PriceOracleCalculatedState())
}
