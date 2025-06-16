package io.constellation.price_oracle.shared_data.calculated_state

import java.nio.charset.StandardCharsets

import cats.data.OptionT
import cats.effect.{Async, Ref}
import cats.syntax.all._

import io.constellationnetwork.currency.dataApplication.L0NodeContext
import io.constellationnetwork.schema.ID.Id
import io.constellationnetwork.schema.SnapshotOrdinal
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.schema.priceOracle.TokenPair
import io.constellationnetwork.security.hash.Hash
import io.constellationnetwork.security.{Hasher, SecurityProvider}

import eu.timepit.refined.types.all.NonNegInt
import io.circe.syntax.EncoderOps
import io.constellation.price_oracle.shared_data.app.ApplicationConfig
import io.constellation.price_oracle.shared_data.types._
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait CalculatedStateService[F[_]] {
  def get: F[CalculatedState]
  def update(snapshotOrdinal: SnapshotOrdinal, epochProgress: EpochProgress, state: PriceOracleCalculatedState): F[Boolean]
  def hash(state: PriceOracleCalculatedState): F[Hash]
}

object CalculatedStateService {
  def make[F[_]: Async](config: ApplicationConfig): F[CalculatedStateService[F]] =
    Ref.of[F, CalculatedState](CalculatedState.empty).map { stateRef =>
      new CalculatedStateService[F] {
        private def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

        override def get: F[CalculatedState] =
          stateRef.get.flatMap(state => logger.info(s"CalculatedStateService: ${state}").map(_ => state))

        override def update(snapshotOrdinal: SnapshotOrdinal, epochProgress: EpochProgress, state: PriceOracleCalculatedState): F[Boolean] =
          config.intervalsConfig(epochProgress).flatMap { intervalConfig =>
            stateRef.modify { currentState =>
              val currentCalculatedState = currentState.state

              (
                CalculatedState(
                  snapshotOrdinal,
                  currentCalculatedState.combine(state, intervalConfig.maxSize)
                ),
                true
              )
            }
          }

        override def hash(state: PriceOracleCalculatedState): F[Hash] = Async[F].delay {
          val jsonState = state.asJson.deepDropNullValues.noSpaces
          Hash.fromBytes(jsonState.getBytes(StandardCharsets.UTF_8))
        }
      }
    }

  def calculateAveragePrice(priceUpdates: Map[Id, List[PriceUpdate]], windowSize: NonNegInt): BigDecimal =
    calculateAveragePrice(priceUpdates.values.toList, windowSize)

  private def calculateAveragePrice(priceUpdates: List[List[PriceUpdate]], windowSize: NonNegInt): BigDecimal = {
    def average(priceUpdates: List[PriceUpdate]) =
      if (priceUpdates.isEmpty) BigDecimal(0)
      else {
        val lambda = 0.25 // decay factor, can be adjusted between 0 and 1
        priceUpdates.tail.foldLeft(priceUpdates.head.prices.median) { (ewma, priceUpdate) =>
          lambda * priceUpdate.prices.median + ((1 - lambda) * ewma)
        }
      }

    val filtered = priceUpdates.filter(_.nonEmpty).map(_.takeRight(windowSize.value))
    if (filtered.isEmpty) BigDecimal(0) else filtered.map(average).sum / filtered.size
  }

  def getEpochProgress[F[_]: Async](context: L0NodeContext[F]) =
    OptionT(context.getLastSynchronizedGlobalSnapshot).map(_.epochProgress).getOrElseF {
      val message = "Could not get last synchronized global snapshot data"
      new Exception(message).raiseError[F, EpochProgress]
    }

}
