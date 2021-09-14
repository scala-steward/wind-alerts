package com.uptech.windalerts

import cats.effect.Resource.eval
import cats.effect._
import com.softwaremill.sttp.quick.backend
import com.typesafe.config.ConfigFactory.parseFileAnySyntax
import com.uptech.windalerts.config._
import com.uptech.windalerts.config.beaches.{Beaches, _}
import com.uptech.windalerts.config.secrets.SurfsUp
import com.uptech.windalerts.config.swellAdjustments.Adjustments
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.infrastructure.EmailSender
import com.uptech.windalerts.infrastructure.beaches.{WWBackedSwellsService, WWBackedTidesService, WWBackedWindsService}
import com.uptech.windalerts.infrastructure.endpoints.{BeachesEndpoints, logger}
import com.uptech.windalerts.infrastructure.endpoints.logger.requestLogger
import io.circe.config.parser.decodePathF
import org.http4s.client.middleware.ResponseLogger
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.RequestLogger
import org.http4s.server.{Router, Server => H4Server}

object BeachesServer extends IOApp {
  def createServer[F[_] : ContextShift : ConcurrentEffect : Timer](): Resource[F, H4Server[F]] =
    for {
      surfsUp <- eval(decodePathF[F, SurfsUp](parseFileAnySyntax(secrets.getConfigFile()), "surfsUp"))
      beaches <- eval(decodePathF[F, Beaches](parseFileAnySyntax(config.getConfigFile("beaches.json")), "surfsUp"))
      swellAdjustments <- eval(decodePathF[F, Adjustments](parseFileAnySyntax(config.getConfigFile("swellAdjustments.json")), "surfsUp"))
      willyWeatherAPIKey = surfsUp.willyWeather.key

      beachService = new BeachService[F](
        new WWBackedWindsService[F](willyWeatherAPIKey),
        new WWBackedTidesService[F](willyWeatherAPIKey, beaches.toMap()),
        new WWBackedSwellsService[F](willyWeatherAPIKey, swellAdjustments))

      httpApp = Router(
        "/v1/beaches" -> new BeachesEndpoints[F](beachService).allRoutes(),
      ).orNotFound
      server <- BlazeServerBuilder[F]
        .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
        .withHttpApp(httpApp)
        .resource
    } yield server

  def run(args: List[String]): IO[ExitCode] = createServer.use(_ => IO.never).as(ExitCode.Success)


}