package com.uptech.windalerts

import cats.effect.{ConcurrentEffect, ContextShift, IO, Timer}
import cats.implicits._
import com.uptech.windalerts.Swells.Swell
import fs2.Stream
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import fs2.Stream
import org.http4s.HttpService
import org.http4s.circe.{jsonEncoderOf, jsonOf}

import scala.concurrent.ExecutionContext.global

object WindalertsServer {

  def stream[F[_] : ConcurrentEffect](implicit T: Timer[F], C: ContextShift[F]): Stream[F, Nothing] = {
    for {
      client <- BlazeClientBuilder[F](global).stream

      windAlg = Winds.impl[F](client)
      swell = Swells.impl[F](client)

      httpApp = (WindalertsRoutes.windsRoutes[F](windAlg, swell)).orNotFound

      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      exitCode <- BlazeServerBuilder[F]
        .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
    }.drain
}