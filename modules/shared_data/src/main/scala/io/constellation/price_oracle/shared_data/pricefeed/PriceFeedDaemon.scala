package io.constellation.price_oracle.shared_data.pricefeed

import cats.effect.Async
import cats.effect.std.Supervisor

import io.constellationnetwork.node.shared.domain.Daemon

import io.constellation.price_oracle.shared_data.app.ApplicationConfig

object PriceFeedDaemon {
  def make[F[_]: Async: Supervisor](priceFeedService: PriceFeedService[F], config: ApplicationConfig): Daemon[F] =
    Daemon.periodic(priceFeedService.updatePrices(), config.pollInterval)
}
