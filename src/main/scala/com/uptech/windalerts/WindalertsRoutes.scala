package com.uptech.windalerts

import cats.implicits._
import com.uptech.windalerts.Domain.{BeachId, BeachStatus, TideStatus}
import org.http4s.dsl.Http4sDsl

object WindalertsRoutes {
  import DomainCodec._
  import cats.effect._
  import org.http4s._

  def windsRoutes[F[_]: Sync](W: Winds[F], S: Swells[F], T: Tides[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "beaches" / IntVar(beachId) / "currentStatus" =>
        for {
          wind  <- W.get(BeachId(beachId))
          swell <- S.get(BeachId(beachId))
          tide  <- T.get(BeachId(beachId))
          resp  <- Ok(BeachStatus(wind, TideStatus(tide, swell)))
        } yield resp
    }
  }

}