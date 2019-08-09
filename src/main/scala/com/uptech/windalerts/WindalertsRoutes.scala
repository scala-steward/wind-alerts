package com.uptech.windalerts

import cats.effect.Sync
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

object WindalertsRoutes {

  def windsRoutes[F[_]: Sync](W: Winds[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "winds" =>
        for {
          wind <- W.get
          resp <- Ok(wind)
        } yield resp
    }
  }

}