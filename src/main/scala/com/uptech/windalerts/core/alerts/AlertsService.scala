package com.uptech.windalerts.core.alerts

import cats.Bifunctor.ops.toAllBifunctorOps
import cats.Functor
import cats.data.EitherT
import cats.effect.Sync
import com.uptech.windalerts.Repos
import com.uptech.windalerts.core.{AlertNotFoundError, SurfsUpError}
import com.uptech.windalerts.core.alerts.domain.AlertT
import com.uptech.windalerts.core.user.{AuthenticationService, UserRolesService, UserService}
import com.uptech.windalerts.domain.domain.{AlertDTO, AlertRequest, UserId}

class AlertsService[F[_] : Sync](usersService: UserService[F], userRolesService: UserRolesService[F], repo: Repos[F]) {
  def createAlert(u: UserId, r: AlertRequest) = {
    for {
      dbUser <- usersService.getUser(u.id)
      _ <- userRolesService.authorizePremiumUsers(dbUser)
      saved <- save(u, r)
    } yield saved
  }

  private def save(u: UserId, r: AlertRequest):cats.data.EitherT[F, SurfsUpError, AlertDTO] = {
    EitherT.liftF(repo.alertsRepository().save(r, u.id)).map(_.asDTO())
  }

  def update(alertId: String, u: UserId, r: AlertRequest):EitherT[F, SurfsUpError, AlertDTO] = {
    for {
      dbUser <- usersService.getUser(u.id)
      _ <- userRolesService.authorizeAlertEditRequest(dbUser, alertId, r).leftWiden[SurfsUpError]
      saved <- update(u.id, alertId, r).map(_.asDTO()).leftWiden[SurfsUpError]
    } yield saved
  }

  def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, AlertNotFoundError, AlertT] = repo.alertsRepository().update(requester, alertId, updateAlertRequest)

  def getAllForUser(user: String): F[AlertsT] = repo.alertsRepository().getAllForUser(user)


  def getAllForDayAndTimeRange()(implicit F: Functor[F]): EitherT[F, Exception, Seq[AlertT]] = {
    EitherT.liftF(repo.alertsRepository().getAllEnabled())
      .map(_.filter(_.isToBeAlertedNow()))
  }

  def deleteT(requester: String, alertId: String) = {
    repo.alertsRepository().delete(requester, alertId)
  }
}


