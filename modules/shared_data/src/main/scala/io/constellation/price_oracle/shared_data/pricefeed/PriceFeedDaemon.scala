package io.constellation.price_oracle.shared_data.pricefeed

import cats.effect.Async
import cats.effect.std.Supervisor

import io.constellationnetwork.node.shared.domain.Daemon

import io.constellation.price_oracle.shared_data.app.ApplicationConfig
import io.constellation.price_oracle.shared_data.app.ApplicationConfig.IntervalsConfig

object PriceFeedDaemon {
  def make[F[_]: Async: Supervisor](priceFeedService: PriceFeedService[F], config: IntervalsConfig): Daemon[F] =
    Daemon.periodic(priceFeedService.retrievePrices(), config.poll)
}
