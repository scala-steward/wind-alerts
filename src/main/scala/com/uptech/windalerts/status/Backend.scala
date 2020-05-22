package com.uptech.windalerts.status

import cats.effect._
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.{LazyRepos, Repos}
import com.uptech.windalerts.domain.{HttpErrorHandler, config, secrets, swellAdjustments}
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.CORS

object Backend {

  def start[F[_]: ConcurrentEffect: ContextShift: Timer](repos:Repos[F]): Resource[F, Server[F]] = {
    val apiKey = secrets.read.surfsUp.willyWeather.key
    implicit val backend = HttpURLConnectionBackend()
    for {
      blocker <- Blocker[F]
      beaches = new BeachService[F](new WindsService[F](apiKey), new TidesService[F](apiKey, repos), new SwellsService[F](apiKey, swellAdjustments.read))

      server <- BlazeServerBuilder[F]
        .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
        .withHttpApp(Routes[F](beaches, new HttpErrorHandler[F]).allRoutes().orNotFound)
        .resource
    } yield server
  }

}
