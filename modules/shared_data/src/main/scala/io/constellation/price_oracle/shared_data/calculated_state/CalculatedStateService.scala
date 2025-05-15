package io.constellation.price_oracle.shared_data.calculated_state

import java.nio.charset.StandardCharsets

import cats.data.NonEmptyList
import cats.effect.Ref
import cats.effect.kernel.Async
import cats.syntax.all._

import io.constellationnetwork.schema.SnapshotOrdinal
import io.constellationnetwork.schema.swap.CurrencyId
import io.constellationnetwork.security.hash.Hash

import eu.timepit.refined.types.numeric.NonNegInt
import io.circe.syntax.EncoderOps
import io.constellation.price_oracle.shared_data.app.ApplicationConfig
import io.constellation.price_oracle.shared_data.types.{CalculatedState, PriceOracleCalculatedState, PriceRecord}

trait CalculatedStateService[F[_]] {
  def get: F[CalculatedState]
  def update(snapshotOrdinal: SnapshotOrdinal, state: PriceOracleCalculatedState): F[Boolean]
  def hash(state: PriceOracleCalculatedState): F[Hash]
  def calculateAveragePrice(currencyId: Option[CurrencyId]): F[Option[BigDecimal]]
}

object CalculatedStateService {
  def make[F[_]: Async](config: ApplicationConfig): F[CalculatedStateService[F]] =
    Ref.of[F, CalculatedState](CalculatedState.empty).map { stateRef =>
      val maxSize = NonNegInt.unsafeFrom((config.intervals.storage / config.intervals.poll).toInt)
      new CalculatedStateService[F] {
        override def get: F[CalculatedState] = stateRef.get

        override def update(snapshotOrdinal: SnapshotOrdinal, state: PriceOracleCalculatedState): F[Boolean] =
          stateRef.modify { currentState =>
            val currentCalculatedState = currentState.state

            (
              CalculatedState(
                snapshotOrdinal,
                currentCalculatedState.combine(state, maxSize)
              ),
              true
            )
          }

        override def hash(state: PriceOracleCalculatedState): F[Hash] = Async[F].delay {
          val jsonState = state.asJson.deepDropNullValues.noSpaces
          Hash.fromBytes(jsonState.getBytes(StandardCharsets.UTF_8))
        }

        override def calculateAveragePrice(currencyId: Option[CurrencyId]): F[Option[BigDecimal]] =
          for {
            state <- stateRef.get
          } yield state.state.priceState.get(currencyId).map(CalculatedStateService.calculateAveragePrice)
      }
    }

  def calculateAveragePrice(prices: NonEmptyList[PriceRecord]): BigDecimal = {
    val lambda = 0.25 // decay factor, can be adjusted between 0 and 1
    prices.tail.foldLeft(prices.head.median()) { (ewma, priceRecord) =>
      (lambda * priceRecord.median()) + ((1 - lambda) * ewma)
    }
  }
}
