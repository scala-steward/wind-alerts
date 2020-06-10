package com.uptech.windalerts.alerts

import cats.data.EitherT
import cats.effect.Effect
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.{HttpErrorHandler, http}
import com.uptech.windalerts.users.{AuthenticationService, UserService}
import io.scalaland.chimney.dsl._
import org.http4s.AuthedRoutes

class AlertsEndpoints[F[_] : Effect](alertService: AlertsService[F], usersService: UserService[F], auth: AuthenticationService[F], httpErrorHandler: HttpErrorHandler[F]) extends http[F](httpErrorHandler) {
  def allUsersService(): AuthedRoutes[UserId, F] =
    AuthedRoutes {
      case authReq@GET -> Root as user => {
        handleOkNoDecode(user, (u: UserId) =>
            EitherT.liftF(alertService.getAllForUser(user.id))
              .map(alerts => Alerts(alerts.alerts.map(a => a.into[Alert].withFieldComputed(_.id, u => u._id.toHexString).transform)))
        )
      }

      case authReq@DELETE -> Root / alertId as user => {
        handleNoContent(authReq, user, (u: UserId, r: AlertRequest) => alertService.deleteT(u.id, alertId))
      }

      case authReq@PUT -> Root / alertId as user => {
        handleOk(authReq, user, (u: UserId, r: AlertRequest) => {
          for {
            dbUser <- usersService.getUser(u.id)
            _ <- auth.authorizePremiumUsers(dbUser)
            saved <- alertService.updateT(u.id, alertId, r).map(r => r.into[Alert].withFieldComputed(_.id, u => u._id.toHexString).transform)
          } yield saved
        }
        )
      }

      case authReq@POST -> Root as user => {
        handleCreated(authReq, user, (u: UserId, r: AlertRequest) => {
          for {
            dbUser <- usersService.getUser(u.id)
            _ <- auth.authorizePremiumUsers(dbUser)
            saved <- EitherT.liftF(alertService.save(r, u.id)).map(r => r.into[Alert].withFieldComputed(_.id, u => u._id.toHexString).transform)
          } yield saved
        }
        )
      }
    }
}
