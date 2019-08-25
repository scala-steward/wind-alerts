package com.uptech.windalerts

import cats.implicits._
import com.uptech.windalerts.Domain.{BeachId, Beach, Tide}
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
        val id = BeachId(beachId)
        for {
          wind  <- W.get(id)
          swell <- S.get(id)
          tide  <- T.get(id)
          resp  <- Ok(Beach(wind, Tide(tide, swell)))
        } yield resp
    }
  }

}