package com.uptech.windalerts.infrastructure.endpoints

import cats.data.OptionT
import cats.effect.Effect
import cats.implicits._
import com.uptech.windalerts.core.alerts.AlertsService
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.{UserNotFoundError, _}
import org.http4s.AuthedRoutes
import org.http4s.dsl.Http4sDsl


class AlertsEndpoints[F[_] : Effect](alertService: AlertsService[F]) extends Http4sDsl[F]  {
  def allUsersService(): AuthedRoutes[UserId, F] =
    AuthedRoutes {

      case _@GET -> Root as user => {
        OptionT.liftF(
          for {
            response <- alertService.getAllForUser(user.id).map(alerts => Alerts(alerts.alerts.map(_.asDTO())))
            resp <- Ok(response)
          } yield resp)
      }

      case _@DELETE -> Root / alertId as user => {
        OptionT.liftF(
          (for {
            resp <- alertService.deleteT(user.id, alertId)
          } yield resp).value.flatMap {
            case Right(_) => NoContent()
            case Left(AlertNotFoundError(_)) => NotFound("Alert not found")
          })
      }

      case authReq@PUT -> Root / alertId as user => {
        OptionT.liftF(authReq.req.decode[AlertRequest] {
          request =>
            (for {
              response <- alertService.update(alertId, user, request)
            } yield response).value.flatMap {
              case Right(response) => Ok(response)
              case Left(OperationNotAllowed(message)) => Forbidden(message)
              case Left(AlertNotFoundError(_)) => NotFound("Alert not found")
              case Left(UserNotFoundError(_)) => NotFound("User not found")
            }
        })
      }

      case authReq@POST -> Root as user => {
        OptionT.liftF(authReq.req.decode[AlertRequest] {
          request =>
            (for {
              response <- alertService.createAlert(user, request)
            } yield response).value.flatMap {
              case Right(response) => Ok(response)
              case Left(UserNotFoundError(_)) => NotFound("User not found")
              case Left(OperationNotAllowed(message)) => Forbidden(message)
            }
        })
      }

    }

}
