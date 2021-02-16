package com.uptech.windalerts.infrastructure.endpoints


import cats.effect.Effect
import cats.implicits._
import com.uptech.windalerts.domain.HttpErrorHandler
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain.BeachId
import com.uptech.windalerts.status.BeachService
import org.http4s._
import org.http4s.dsl.Http4sDsl

class BeachesEndpoints[F[_]: Effect](B: BeachService[F], H: HttpErrorHandler[F]) extends Http4sDsl[F]{
  def allRoutes() = HttpRoutes.of[F] {
    case GET -> Root / "beaches" / IntVar(id) / "currentStatus" =>
      getStatus(B, id, H)
    case GET -> Root / "v1" / "beaches" / IntVar(id) / "currentStatus" =>
      getStatus(B, id, H)
  }

  private def getStatus(B: BeachService[F], id: Int, H: HttpErrorHandler[F]) = {
    val eitherStatus = for {
      status <- B.get(BeachId(id))
    } yield status
    eitherStatus.value.flatMap {
      case Right(value) => Ok(value)
      case Left(error) => H.handleThrowable(error)
    }
  }
}
