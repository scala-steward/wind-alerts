package com.uptech.windalerts.core.alerts

import cats.Functor
import cats.data.EitherT
import cats.effect.Sync
import com.uptech.windalerts.Repos
import com.uptech.windalerts.core.alerts.domain.AlertT
import com.uptech.windalerts.core.user.{AuthenticationService, UserService}
import com.uptech.windalerts.domain.SurfsUpError
import com.uptech.windalerts.domain.domain.{Alert, AlertRequest, SurfsUpEitherT, UserId}

class AlertsService[F[_] : Sync](usersService: UserService[F], auth: AuthenticationService[F], repo: Repos[F]) {
  def createAlert(u: UserId, r: AlertRequest) = {
    for {
      dbUser <- usersService.getUser(u.id)
      _ <- auth.authorizePremiumUsers(dbUser)
      saved <- save(u, r)
    } yield saved
  }

  private def save(u: UserId, r: AlertRequest):SurfsUpEitherT[F, Alert] = {
    EitherT.liftF(repo.alertsRepository().save(r, u.id)).map(_.asDTO())
  }

  def update(alertId: String, u: UserId, r: AlertRequest) = {
    for {
      dbUser <- usersService.getUser(u.id)
      _ <- auth.authorizeAlertEditRequest(dbUser, alertId, r)
      saved <- updateT(u.id, alertId, r).map(_.asDTO())
    } yield saved
  }

  def updateT(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, SurfsUpError, AlertT] = repo.alertsRepository().updateT(requester, alertId, updateAlertRequest)

  def getAllForUser(user: String): F[AlertsT] = repo.alertsRepository().getAllForUser(user)


  def getAllForDayAndTimeRange()(implicit F: Functor[F]): EitherT[F, Exception, Seq[AlertT]] = {
    EitherT.liftF(repo.alertsRepository().getAllEnabled())
      .map(_.filter(_.isToBeAlertedNow()))
  }

  def deleteT(requester: String, alertId: String): EitherT[F, SurfsUpError, Unit] = {
    repo.alertsRepository().delete(requester, alertId)
  }
}


