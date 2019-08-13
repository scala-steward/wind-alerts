package com.uptech.windalerts

import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.Domain.{BeachId, BeachStatus, High, TideStatus, WindStatus}
import com.uptech.windalerts.Winds.WindError
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

object WindalertsRoutes {
  import DomainCodec._
  import cats.effect._
  import io.circe._
  import io.circe.generic.auto._
  import org.http4s._
  import org.http4s.circe._

  def windsRoutes[F[_]: Sync](W: Winds[F], S: Swells[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "beaches" / IntVar(beachId) / "currentStatus" =>
        for {
          wind  <- W.get(BeachId(beachId))
          swell <- S.get(BeachId(beachId))
          resp  <- Ok(BeachStatus(wind.get, swell.get, High))
        } yield resp
    }
  }

}