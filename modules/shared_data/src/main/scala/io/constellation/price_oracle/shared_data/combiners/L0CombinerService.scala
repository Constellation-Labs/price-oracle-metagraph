package io.constellation.price_oracle.shared_data.combiners

import cats.effect.Async

import io.constellationnetwork.currency.dataApplication.{DataState, L0NodeContext}
import io.constellationnetwork.security.signature.Signed

import io.constellation.price_oracle.shared_data.types.{PriceOracleCalculatedState, PriceOracleOnChainState, PriceUpdate}

trait L0CombinerService[F[_]] {
  def combine(
    oldState: DataState[PriceOracleOnChainState, PriceOracleCalculatedState],
    updates: List[Signed[PriceUpdate]]
  )(implicit context: L0NodeContext[F]): F[DataState[PriceOracleOnChainState, PriceOracleCalculatedState]]
}

object L0CombinerService {
  def make[F[_]: Async]: L0CombinerService[F] = new L0CombinerService[F] {
    override def combine(oldState: DataState[PriceOracleOnChainState, PriceOracleCalculatedState], updates: List[Signed[PriceUpdate]])(
      implicit context: L0NodeContext[F]
    ): F[DataState[PriceOracleOnChainState, PriceOracleCalculatedState]] =
      ???
  }
}
