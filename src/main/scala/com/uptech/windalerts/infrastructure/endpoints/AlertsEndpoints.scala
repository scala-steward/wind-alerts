package com.uptech.windalerts.infrastructure.endpoints

import cats.data.OptionT
import cats.effect.Effect
import cats.implicits._
import com.uptech.windalerts.core.{AlertNotFoundError, OperationNotAllowed, UserNotFoundError}
import com.uptech.windalerts.core.alerts.AlertsService
import codecs._
import dtos._
import com.uptech.windalerts.config._
import com.uptech.windalerts.core.user.UserIdMetadata
import org.http4s.AuthedRoutes
import org.http4s.dsl.Http4sDsl


class AlertsEndpoints[F[_] : Effect](alertService: AlertsService[F]) extends Http4sDsl[F]  {
  def allUsersService(): AuthedRoutes[UserIdMetadata, F] =
    AuthedRoutes {

      case _@GET -> Root as user => {
        OptionT.liftF(
          for {
            response <- alertService.getAllForUser(user.userId.id).map(alerts => AlertsDTO(alerts.alerts.map(_.asDTO())))
            resp <- Ok(response)
          } yield resp)
      }

      case _@DELETE -> Root / alertId as user => {
        OptionT.liftF(
          (for {
            resp <- alertService.delete(user.userId.id, alertId)
          } yield resp).value.flatMap {
            case Right(_) => NoContent()
            case Left(AlertNotFoundError(_)) => NotFound("Alert not found")
          })
      }

      case authReq@PUT -> Root / alertId as user => {
        OptionT.liftF(authReq.req.decode[AlertRequest] {
          request =>
            (for {
              response <- alertService.update(alertId, user.userId, user.userType, request)
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
              response <- alertService.createAlert(user.userId, user.userType, request)
            } yield response).value.flatMap {
              case Right(response) => Created(response)
              case Left(OperationNotAllowed(message)) => Forbidden(message)
            }
        })
      }

    }

}
