package com.uptech.windalerts

import cats.{Monad, Parallel}
import cats.effect.Resource.eval
import cats.effect._
import com.softwaremill.sttp.quick.backend
import com.typesafe.config.ConfigFactory.parseFileAnySyntax
import com.uptech.windalerts.config.{beaches, _}
import com.uptech.windalerts.config.beaches.{Beaches, _}
import com.uptech.windalerts.config.swellAdjustments.Adjustments
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.infrastructure.beaches.{WWBackedSwellsService, WWBackedTidesService, WWBackedWindsService}
import com.uptech.windalerts.infrastructure.endpoints.BeachesEndpoints
import io.circe.config.parser.decodePathF
import org.http4s.{Response, Status}
import org.http4s.implicits._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.{Router, Server => H4Server}

object BeachesServer extends IOApp {

  def createServer[F[_] : ContextShift : ConcurrentEffect : Timer : Parallel]()(implicit M: Monad[F]): Resource[F, H4Server] = {
    for {
      beaches <- eval(decodePathF[F, Beaches](parseFileAnySyntax(config.getConfigFile("beaches.json")), "surfsUp"))
      swellAdjustments <- eval(decodePathF[F, Adjustments](parseFileAnySyntax(config.getConfigFile("swellAdjustments.json")), "surfsUp"))
      willyWeatherAPIKey = sys.env("WILLY_WEATHER_KEY")
      getWindStatus = WWBackedWindsService.get(willyWeatherAPIKey)
      getTideStatus = WWBackedTidesService.get(willyWeatherAPIKey, beaches.toMap())
      getSwellsStatus = WWBackedSwellsService.get(willyWeatherAPIKey, swellAdjustments)

      httpApp = Router(
        "/v1/beaches" -> new BeachesEndpoints[F]()
          .allRoutes(getWindStatus, getTideStatus, getSwellsStatus)
      ).orNotFound
      server <- BlazeServerBuilder[F]
        .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
        .withHttpApp(httpApp)
        .withServiceErrorHandler(_ => {
          case e: Throwable =>
            logger.error("Exception ", e)
            M.pure(Response[F](status = Status.InternalServerError))
        })
        .resource
    } yield server
  }
    def run(args: List[String]): IO[ExitCode] = createServer.use(_ => IO.never).as(ExitCode.Success)


  }
