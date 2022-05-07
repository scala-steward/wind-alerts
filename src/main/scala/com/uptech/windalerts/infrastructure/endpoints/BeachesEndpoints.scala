package com.uptech.windalerts.infrastructure.endpoints


import cats.Parallel
import cats.effect.Effect
import cats.implicits._
import com.uptech.windalerts.core.BeachNotFoundError
import com.uptech.windalerts.core.beaches.BeachService
import codecs._
import com.uptech.windalerts.core.beaches.SwellsService.GetSwellsStatus
import com.uptech.windalerts.core.beaches.TidesService.GetTidesStatus
import com.uptech.windalerts.core.beaches.WindsService.GetWindStatus
import com.uptech.windalerts.core.beaches.domain.BeachId
import org.http4s._
import org.http4s.dsl.Http4sDsl

class BeachesEndpoints[F[_] : Effect](implicit P: Parallel[F]) extends Http4sDsl[F] {
  def allRoutes(implicit
                  getWindStatus: GetWindStatus[F],
                  getTideStatus: GetTidesStatus[F],
                  getSwellsStatus: GetSwellsStatus[F]) = HttpRoutes.of[F] {
    case GET -> Root / IntVar(id) / "currentStatus" =>
      getStatus(id)
  }

  private def getStatus(id: Int)(implicit
                                 getWindStatus: GetWindStatus[F],
                                 getTideStatus: GetTidesStatus[F],
                                 getSwellsStatus: GetSwellsStatus[F],
                                 P: Parallel[F]) = {
    val eitherStatus = for {
      status <- BeachService.getStatus(BeachId(id))
    } yield status
    eitherStatus.value.flatMap {
      case Right(value) => Ok(value)
      case Left(BeachNotFoundError(_)) => NotFound(s"Beach not found $id")
    }
  }
}