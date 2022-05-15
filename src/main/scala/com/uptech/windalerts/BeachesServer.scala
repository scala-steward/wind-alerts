package com.uptech.windalerts

import cats.effect.Resource.eval
import cats.effect._
import cats.mtl.Handle
import cats.{Applicative, Parallel}
import com.softwaremill.sttp.quick.backend
import com.typesafe.config.ConfigFactory.parseFileAnySyntax
import com.uptech.windalerts.config._
import com.uptech.windalerts.config.beaches.{Beaches, _}
import com.uptech.windalerts.config.swellAdjustments.Adjustments
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.infrastructure.beaches.{WWBackedSwellsService, WWBackedTidesService, WWBackedWindsService}
import com.uptech.windalerts.infrastructure.endpoints.BeachesEndpoints
import io.circe.config.parser.decodePathF
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._
import org.http4s.server.{Router, Server => H4Server}
import org.http4s.{Response, Status}

import scala.concurrent.ExecutionContext

object BeachesServer extends IOApp {
  def createServer[F[_]](implicit Sync: Sync[F], H: Handle[F, Throwable], AS: Async[F], CE: ConcurrentEffect[F], CS: ContextShift[F], T: Timer[F], P:Parallel[F]): Resource[F, H4Server] = {

    for {
      beaches <- eval(decodePathF[F, Beaches](parseFileAnySyntax(config.getConfigFile("beaches.json")), "surfsUp"))
      swellAdjustments <- eval(decodePathF[F, Adjustments](parseFileAnySyntax(config.getConfigFile("swellAdjustments.json")), "surfsUp"))
      willyWeatherAPIKey = sys.env("WILLY_WEATHER_KEY")

      beachService = new BeachService[F](
        new WWBackedWindsService[F](willyWeatherAPIKey),
        new WWBackedTidesService[F](willyWeatherAPIKey, beaches.toMap()),
        new WWBackedSwellsService[F](willyWeatherAPIKey, swellAdjustments))


      httpApp = Router(
        "/v1/beaches" -> new BeachesEndpoints[F](beachService).allRoutes
      ).orNotFound
      server <- BlazeServerBuilder(ExecutionContext.global)
        .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
        .withHttpApp(httpApp)
        .withServiceErrorHandler(_ => {
          case e: Throwable =>
            logger.error("Exception ", e)
            Applicative[F].pure(Response[F](status = Status.InternalServerError))
        })
        .resource
    } yield server
  }

  override def run(args: List[String]): IO[ExitCode] = {
    createServer[IO].use(_ => IO.never).as(ExitCode.Success)
  }



}

