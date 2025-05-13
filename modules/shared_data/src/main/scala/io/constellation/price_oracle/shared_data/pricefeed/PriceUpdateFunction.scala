package io.constellation.price_oracle.shared_data.pricefeed

import java.time.Instant

import cats.effect.Async
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._

import io.constellationnetwork.schema.NonNegFraction
import io.constellationnetwork.schema.artifact.PricingUpdate
import io.constellationnetwork.schema.priceOracle.PricingUpdateReference
import io.constellationnetwork.schema.swap.CurrencyId

import io.constellation.price_oracle.shared_data.types.PriceUpdate

trait PriceUpdateFunction[F[_]] {
  def apply(currencyId: Option[CurrencyId], price: BigDecimal): F[Unit]
}

object PriceUpdateFunction {
  def make[F[_]: Async](): PriceUpdateFunction[F] = new PriceUpdateFunction[F] {
    override def apply(currencyId: Option[CurrencyId], price: BigDecimal): F[Unit] =
      createPriceUpdate(currencyId, price).map { update =>
        ???
      }

    private def createPriceUpdate(currencyId: Option[CurrencyId], price: BigDecimal): F[PriceUpdate] =
      for {
        parent <- getLastReference
        fractionalValue <- NonNegFraction.fromBigDecimal(price)
      } yield {
        val update = PricingUpdate(
          currencyId = currencyId,
          fractionalValue = fractionalValue,
          markOverride = false,
          parent = parent
        )
        PriceUpdate(update, Instant.now().getEpochSecond)
      }

    private def getLastReference: F[PricingUpdateReference] = PricingUpdateReference.empty.pure[F]
  }
}
