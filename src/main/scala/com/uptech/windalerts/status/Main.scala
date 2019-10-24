package com.uptech.windalerts.status

import java.io.File

import cats.effect.{IO, _}
import cats.implicits._
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain.BeachId
import com.uptech.windalerts.domain.{AppSettings, HttpErrorHandler, config}
import io.circe.config.parser
import io.circe.generic.auto._
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
object  Main extends IOApp {
  private val logger = getLogger


  def allRoutes( B: Beaches.Service, H:HttpErrorHandler[IO]) = HttpRoutes.of[IO] {
    case GET -> Root / "v1" / "beaches" / IntVar(id) / "currentStatus" =>
      Ok(B.get(BeachId(id)))
    case GET -> Root / "beaches" / IntVar(id) / "currentStatus" =>
      Ok(B.get(BeachId(id)))
  }.orNotFound

  def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- IO(getLogger.error("Starting"))
      conf <- IO(config.readConf)
      apiKey <- IO(conf.surfsup.willyWeather.key)
      beaches <- IO(Beaches.ServiceImpl(Winds.impl(apiKey), Swells.impl(apiKey), Tides.impl(apiKey)))
      httpErrorHandler <- IO(new HttpErrorHandler[IO])
      server <- BlazeServerBuilder[IO]
        .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
        .withHttpApp(allRoutes( beaches, httpErrorHandler))
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    } yield server
  }

}