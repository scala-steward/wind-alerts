package com.uptech.windalerts.status

import cats.effect.{IO, _}
import cats.implicits._
import com.uptech.windalerts.domain.Domain.BeachId
import com.uptech.windalerts.domain.HttpErrorHandler
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
import com.uptech.windalerts.domain.DomainCodec._


object Main extends IOApp {

  private val logger = getLogger

  logger.error("Starting")


  val beaches = Beaches.ServiceImpl(Winds.impl, Swells.impl, Tides.impl)
  implicit val httpErrorHandler: HttpErrorHandler[IO] = new HttpErrorHandler[IO]

  def allRoutes( B: Beaches.Service, H:HttpErrorHandler[IO]) = HttpRoutes.of[IO] {
    case GET -> Root / "beaches" / IntVar(id) / "currentStatus" =>
      Ok(B.get(BeachId(id)))
  }.orNotFound


  def run(args: List[String]): IO[ExitCode] = {

    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withHttpApp(allRoutes( beaches, httpErrorHandler))
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }

}
