package io.constellation.price_oracle.routes

import cats.effect.Async
import cats.syntax.all._

import io.constellationnetwork.routes.internal._
import io.constellationnetwork.security.SecurityProvider

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import eu.timepit.refined.auto._
import io.constellation.price_oracle.shared_data.calculated_state.CalculatedStateService
import io.constellation.price_oracle.shared_data.types.codecs.{HasherSelector, JsonWithBase64BinaryCodec}
import io.constellation.price_oracle.shared_data.types.{PriceOracleCalculatedState, PriceUpdate}
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.CORS
import org.http4s.{HttpRoutes, Response}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

case class CustomRoutes[F[_]: Async: HasherSelector: SecurityProvider](
  calculatedStateService: CalculatedStateService[F],
  dataUpdateCodec: JsonWithBase64BinaryCodec[F, PriceUpdate]
) extends Http4sDsl[F]
    with PublicRoutes[F] {

  private def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger

  protected def prefixPath: InternalUrlPrefix = "/"

  @derive(encoder, decoder)
  case class CalculatedStateResponse(
    ordinal: Long,
    calculatedState: PriceOracleCalculatedState
  )

  private def getLatestCalculatedState: F[Response[F]] =
    calculatedStateService.get
      .flatMap(state => Ok(CalculatedStateResponse(state.ordinal.value.value, state.state)))

  val public: HttpRoutes[F] = {
    val routes = HttpRoutes.of[F] {
      case GET -> Root / "calculated-state" / "latest" => getLatestCalculatedState
    }

    CORS.policy
      .withAllowCredentials(false)
      .httpRoutes(routes)
  }
}
