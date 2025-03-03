package io.constellation.price_oracle.shared_data.calculated_state

import java.nio.charset.StandardCharsets

import cats.effect.Ref
import cats.effect.kernel.Async
import cats.syntax.all._

import io.constellationnetwork.schema.SnapshotOrdinal
import io.constellationnetwork.security.hash.Hash

import io.circe.syntax.EncoderOps
import io.constellation.price_oracle.shared_data.types.{CalculatedState, PriceOracleCalculatedState}

trait CalculatedStateService[F[_]] {
  def get: F[CalculatedState]
  def update(snapshotOrdinal: SnapshotOrdinal, state: PriceOracleCalculatedState): F[Boolean]
  def hash(state: PriceOracleCalculatedState): F[Hash]
}

object CalculatedStateService {
  def make[F[_]: Async]: F[CalculatedStateService[F]] =
    Ref.of[F, CalculatedState](CalculatedState.empty).map { stateRef =>
      new CalculatedStateService[F] {
        override def get: F[CalculatedState] = stateRef.get

        override def update(snapshotOrdinal: SnapshotOrdinal, state: PriceOracleCalculatedState): F[Boolean] =
          stateRef.modify { currentState =>
            val currentCalculatedState = currentState.state

            (
              CalculatedState(
                snapshotOrdinal,
                currentCalculatedState.combine(state)
              ),
              true
            )
          }

        override def hash(state: PriceOracleCalculatedState): F[Hash] = Async[F].delay {
          val jsonState = state.asJson.deepDropNullValues.noSpaces
          Hash.fromBytes(jsonState.getBytes(StandardCharsets.UTF_8))
        }
      }
    }
}
