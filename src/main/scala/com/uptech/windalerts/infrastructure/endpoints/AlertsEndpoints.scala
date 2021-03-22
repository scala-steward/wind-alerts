package com.uptech.windalerts.infrastructure.endpoints

import cats.data.EitherT
import cats.effect.Effect
import com.uptech.windalerts.core.alerts.AlertsService
import com.uptech.windalerts.core.user.{AuthenticationService, UserService}
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain._
import org.http4s.AuthedRoutes

class AlertsEndpoints[F[_] : Effect](alertService: AlertsService[F], usersService: UserService[F], auth: AuthenticationService[F], httpErrorHandler: HttpErrorHandler[F]) extends http[F](httpErrorHandler) {
  def allUsersService(): AuthedRoutes[UserId, F] =
    AuthedRoutes {
      case _@GET -> Root as user =>
        handleOkNoDecode(user, (_: UserId) => EitherT.liftF(alertService.getAllForUser(user.id)).map(alerts => Alerts(alerts.alerts.map(_.asDTO()))))

      case _@DELETE -> Root / alertId as user =>
        handleNoContentNoDecode(user, (u: UserId) => alertService.deleteT(u.id, alertId))

      case authReq@PUT -> Root / alertId as user =>
        handleOk(authReq, user, (u: UserId, r: AlertRequest) => alertService.update(alertId, u, r))

      case authReq@POST -> Root as user =>
        handleCreated(authReq, user, (u: UserId, r: AlertRequest) => alertService.createAlert(u, r))
    }

}
