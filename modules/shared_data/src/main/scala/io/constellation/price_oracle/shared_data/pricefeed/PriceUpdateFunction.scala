package io.constellation.price_oracle.shared_data.pricefeed

import java.security.KeyPair

import cats.effect.Async
import cats.syntax.all._

import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.schema.priceOracle.TokenPair
import io.constellationnetwork.security.signature.Signed
import io.constellationnetwork.security.{Hasher, SecurityProvider}

import io.circe.syntax._
import io.constellation.price_oracle.shared_data.app.ApplicationConfig
import io.constellation.price_oracle.shared_data.storages.GlobalSnapshotsStorage
import io.constellation.price_oracle.shared_data.types.{PriceUpdate, PriceValues}
import org.http4s.Method._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait PriceUpdateFunction[F[_]] {
  def apply(tokenPair: TokenPair, prices: PriceValues): F[Unit]
}

object PriceUpdateFunction {
  def make[F[_]: Async: SecurityProvider](
    config: ApplicationConfig,
    signingKeyPair: Option[KeyPair],
    client: Client[F],
    globalSnapshotsStorage: GlobalSnapshotsStorage[F]
  )(
    implicit hasher: Hasher[F]
  ): PriceUpdateFunction[F] = new PriceUpdateFunction[F] {
    def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("PriceUpdateFunction")

    private val dsl = new Http4sClientDsl[F] {}
    import dsl._

    override def apply(tokenPair: TokenPair, prices: PriceValues): F[Unit] =
      logger.info(s"received an update: $tokenPair -> $prices").flatMap { _ =>
        if (signingKeyPair.isEmpty) ().pure[F]
        else {
          for {
            latestEpochProgress <- globalSnapshotsStorage.get.map(_.map(_.epochProgress).getOrElse(EpochProgress.MinValue))
            dataUpdate = PriceUpdate(tokenPair, prices, latestEpochProgress)
            signed <- Signed.forAsyncHasher(dataUpdate, signingKeyPair.get)

            _ <- logger.info(s"sending : ${signed.asJson}")

            request = POST(
              signed.asJson,
              config.dataL1ServerUri
            ).withHeaders(Headers("Content-Type" -> "application/json"))

            _ <- client.expect[Unit](request)
          } yield ()
        }
      }
  }
}
