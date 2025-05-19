package io.constellation.price_oracle.shared_data.pricefeed

import cats.effect.Async
import cats.syntax.applicative._
import cats.syntax.functor._

import io.constellationnetwork.schema.priceOracle.TokenPair

import io.constellation.price_oracle.shared_data.app.ApplicationConfig
import io.constellation.price_oracle.shared_data.types.{PriceUpdate, PriceValues}

trait PriceUpdateFunction[F[_]] {
  def apply(tokenPair: TokenPair, prices: PriceValues): F[Unit]
}

object PriceUpdateFunction {
  def make[F[_]: Async](config: ApplicationConfig): PriceUpdateFunction[F] = new PriceUpdateFunction[F] {
    override def apply(tokenPair: TokenPair, prices: PriceValues): F[Unit] =
      PriceUpdate(tokenPair, prices, System.currentTimeMillis()).pure[F].void
  }
}
