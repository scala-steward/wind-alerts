package com.uptech.windalerts.infrastructure.endpoints


import cats.effect.Effect
import cats.implicits._
import com.uptech.windalerts.core.BeachNotFoundError
import com.uptech.windalerts.core.beaches.BeachService
import codecs._
import com.uptech.windalerts.domain.domain.BeachId
import org.http4s._
import org.http4s.dsl.Http4sDsl

class BeachesEndpoints[F[_] : Effect](B: BeachService[F]) extends Http4sDsl[F] {
  def allRoutes() = HttpRoutes.of[F] {
    case GET -> Root / IntVar(id) / "currentStatus" =>
      getStatus(B, id)
  }

  private def getStatus(B: BeachService[F], id: Int) = {
    val eitherStatus = for {
      status <- B.get(BeachId(id))
    } yield status
    eitherStatus.value.flatMap {
      case Right(value) => Ok(value)
      case Left(BeachNotFoundError(_)) => NotFound(s"Beach not found $id")
    }
  }
}
