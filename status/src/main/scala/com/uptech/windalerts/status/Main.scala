package com.uptech.windalerts.status

import cats.effect.{IO, _}
import cats.implicits._
import com.uptech.windalerts.domain.Domain.BeachId
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import com.uptech.windalerts.domain.DomainCodec._
import com.uptech.windalerts.domain.DomainCodec._
import cats.implicits._

object Main extends IOApp {

  def  StatusRoutes(B:Beaches.Service) = HttpRoutes.of[IO] {
    case GET -> Root / "beaches" / IntVar(id) / "currentStatus" =>
      Ok(B.get(BeachId(id)))
  }.orNotFound

  val beaches = Beaches.ServiceImpl(Winds.impl, Swells.impl, Tides.impl)
  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withHttpApp(StatusRoutes(beaches))
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}