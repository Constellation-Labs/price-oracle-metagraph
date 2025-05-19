package io.constellation.price_oracle.shared_data.calculated_state

import io.constellationnetwork.schema.ID.Id
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.schema.priceOracle.TokenPair.DAG_USD
import io.constellationnetwork.security.hex.Hex

import eu.timepit.refined.types.numeric.NonNegInt
import io.constellation.price_oracle.shared_data.pricefeed.PriceFeedId
import io.constellation.price_oracle.shared_data.pricefeed.PriceFeedId.GateIO
import io.constellation.price_oracle.shared_data.types.{PriceUpdate, PriceValue, PriceValues}
import weaver.FunSuiteIO

object CalculatedStateServiceSuite extends FunSuiteIO {

  test("CalculatedStateServiceSuite calculates average price for an empty price list") {
    expect(CalculatedStateService.calculateAveragePrice(Map.empty, NonNegInt(10)) == BigDecimal(0))
  }

  test("CalculatedStateServiceSuite calculates average price for one price") {
    val priceUpdates = Map(
      Id(Hex("01")) -> List(
        priceUpdate(1)
      )
    )
    expect(CalculatedStateService.calculateAveragePrice(priceUpdates, NonNegInt(10)) == BigDecimal(1))
  }

  test("CalculatedStateServiceSuite calculates average price") {
    val priceUpdates = Map(
      Id(Hex("01")) -> List(
        priceUpdate(1),
        priceUpdate(2),
        priceUpdate(3)
      )
    )
    expect(CalculatedStateService.calculateAveragePrice(priceUpdates, NonNegInt(10)) == BigDecimal("1.6875"))
  }

  test("CalculatedStateServiceSuite calculates average price for 3 nodes") {
    val priceUpdates = Map(
      Id(Hex("01")) -> List(
        priceUpdate(1),
        priceUpdate(2),
        priceUpdate(3)
      ),
      Id(Hex("02")) -> List(
        priceUpdate(1),
        priceUpdate(2),
        priceUpdate(3)
      ),
      Id(Hex("03")) -> List(
        priceUpdate(1),
        priceUpdate(2),
        priceUpdate(3)
      )
    )
    expect(CalculatedStateService.calculateAveragePrice(priceUpdates, NonNegInt(10)) == BigDecimal("1.6875"))
  }

  test("CalculatedStateServiceSuite calculates average price with window size 1") {
    val priceUpdates = Map(
      Id(Hex("01")) -> List(
        priceUpdate(1),
        priceUpdate(2),
        priceUpdate(3)
      )
    )
    expect(CalculatedStateService.calculateAveragePrice(priceUpdates, NonNegInt(1)) == BigDecimal(3))
  }

  test("CalculatedStateServiceSuite calculates average price with window size 2") {
    val priceUpdates = Map(
      Id(Hex("01")) -> List(
        priceUpdate(1),
        priceUpdate(2),
        priceUpdate(3)
      )
    )
    expect(CalculatedStateService.calculateAveragePrice(priceUpdates, NonNegInt(2)) == BigDecimal("2.25"))
  }

  test("CalculatedStateServiceSuite calculates average price with window size 3") {
    val priceUpdates = Map(
      Id(Hex("01")) -> List(
        priceUpdate(1),
        priceUpdate(2),
        priceUpdate(3)
      )
    )
    expect(CalculatedStateService.calculateAveragePrice(priceUpdates, NonNegInt(3)) == BigDecimal("1.6875"))
  }

  test("CalculatedStateServiceSuite calculates average price with window size larger than updates") {
    val priceUpdates = Map(
      Id(Hex("01")) -> List(
        priceUpdate(1),
        priceUpdate(2),
        priceUpdate(3)
      )
    )
    expect(CalculatedStateService.calculateAveragePrice(priceUpdates, NonNegInt(5)) == BigDecimal("1.6875"))
  }

  private def priceUpdate(n: Int, priceFeedId: PriceFeedId = GateIO): PriceUpdate =
    PriceUpdate(DAG_USD, PriceValues.of(PriceValue(priceFeedId, BigDecimal(n))), EpochProgress.MinValue)

}
