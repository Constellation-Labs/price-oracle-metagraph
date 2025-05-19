package io.constellation.price_oracle.shared_data.combiners

import cats.effect.Async
import cats.syntax.all._

import scala.collection.immutable.SortedSet

import io.constellationnetwork.currency.dataApplication.{DataState, L0NodeContext}
import io.constellationnetwork.schema.NonNegFraction
import io.constellationnetwork.schema.artifact.{PricingUpdate, SharedArtifact}
import io.constellationnetwork.schema.priceOracle.PriceFraction
import io.constellationnetwork.security.Hasher
import io.constellationnetwork.security.signature.Signed

import io.constellation.price_oracle.shared_data.app.ApplicationConfig
import io.constellation.price_oracle.shared_data.app.ApplicationConfig.IntervalsConfig
import io.constellation.price_oracle.shared_data.calculated_state.CalculatedStateService
import io.constellation.price_oracle.shared_data.types.{PriceOracleCalculatedState, PriceOracleOnChainState, PriceUpdate}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait L0CombinerService[F[_]] {
  def combine(
    oldState: DataState[PriceOracleOnChainState, PriceOracleCalculatedState],
    updates: List[Signed[PriceUpdate]]
  )(implicit context: L0NodeContext[F]): F[DataState[PriceOracleOnChainState, PriceOracleCalculatedState]]
}

object L0CombinerService {
  def make[F[_]: Async](config: ApplicationConfig)(
    implicit hasher: Hasher[F]
  ): L0CombinerService[F] = new L0CombinerService[F] {
    private def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("L0CombinerService")

    override def combine(oldState: DataState[PriceOracleOnChainState, PriceOracleCalculatedState], updates: List[Signed[PriceUpdate]])(
      implicit context: L0NodeContext[F]
    ): F[DataState[PriceOracleOnChainState, PriceOracleCalculatedState]] = {
      logger.info(s"updates: ${updates}")
      if (updates.isEmpty) {
        oldState.pure[F]
      } else {
        logger.info(s"oldState: ${oldState}")
        for {
          lastSyncGlobalEpochProgress <- CalculatedStateService.getEpochProgress(context)
          intervalsConfig <- config.intervalsConfig(lastSyncGlobalEpochProgress)
          calculated = updates.foldLeft(oldState.calculated)((acc, update) => acc.append(update, intervalsConfig.maxSize))
          newSharedArtifacts <- createPricingUpdates(calculated, intervalsConfig)
        } yield {
          val newTokenPairs = newSharedArtifacts.flatMap {
            case update: PricingUpdate => Some(update.price.tokenPair)
            case _                     => None
          }
          val oldSharedArtifacts = SortedSet.from(oldState.sharedArtifacts.toList.collect {
            case pricingUpdate: PricingUpdate => Option.when(!newTokenPairs(pricingUpdate.tokenPair))(pricingUpdate)
            case artifact @ _                 => Some(artifact)
          }.flatten)
          val sharedArtifacts = oldSharedArtifacts ++ newSharedArtifacts
          val newState = oldState.copy(calculated = calculated, sharedArtifacts = sharedArtifacts)
          logger.info(s"newState: ${newState}")
          newState
        }
      }
    }

    private def createPricingUpdates(calculatedState: PriceOracleCalculatedState, config: IntervalsConfig): F[SortedSet[SharedArtifact]] =
      calculatedState.priceState.toList.traverse {
        case (tokenPair, priceUpdates) =>
          val averagePrice = CalculatedStateService.calculateAveragePrice(priceUpdates, config.movingAverageWindowSize)
          for {
            value <- NonNegFraction.fromBigDecimal(BigDecimal(1) / averagePrice)
          } yield {
            val price = PriceFraction(
              tokenPair,
              value
            )
            PricingUpdate(price = price)
          }
      }.map(SortedSet.from(_))
  }
}
