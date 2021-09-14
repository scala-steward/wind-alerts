package com.uptech.windalerts.core.alerts

import cats.Bifunctor.ops.toAllBifunctorOps
import cats.Functor
import cats.data.EitherT
import cats.effect.Sync
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.user.{UserId, UserRepository, UserRolesService}
import com.uptech.windalerts.core.{AlertNotFoundError, SurfsUpError, UserNotFoundError}
import com.uptech.windalerts.infrastructure.endpoints.dtos.{AlertDTO, AlertRequest}

class AlertsService[F[_] : Sync](alertsRepository: AlertsRepository[F],
                                 userRepository: UserRepository[F],
                                 userRolesService: UserRolesService[F]) {
  def createAlert(u: UserId, r: AlertRequest) = {
    for {
      dbUser <- userRepository.getByUserId(u.id).toRight(UserNotFoundError())
      _ <- userRolesService.authorizePremiumUsers(dbUser)
      saved <- save(u, r)
    } yield saved
  }

  private def save(u: UserId, r: AlertRequest):cats.data.EitherT[F, SurfsUpError, AlertDTO] = {
    EitherT.liftF(alertsRepository.save(r, u.id)).map(_.asDTO())
  }

  def update(alertId: String, u: UserId, r: AlertRequest):EitherT[F, SurfsUpError, AlertDTO] = {
    for {
      dbUser <- userRepository.getByUserId(u.id).toRight(UserNotFoundError())
      _ <- userRolesService.authorizeAlertEditRequest(dbUser, alertId, r).leftWiden[SurfsUpError]
      saved <- update(u.id, alertId, r).map(_.asDTO()).leftWiden[SurfsUpError]
    } yield saved
  }

  def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, AlertNotFoundError, Alert] = alertsRepository.update(requester, alertId, updateAlertRequest)

  def getAllForUser(user: String): F[Alerts] = alertsRepository.getAllForUser(user)


  def getAllForDayAndTimeRange()(implicit F: Functor[F]): EitherT[F, Exception, Seq[Alert]] = {
    EitherT.liftF(alertsRepository.getAllEnabled())
      .map(_.filter(_.isToBeAlertedNow()))
  }

  def delete(requester: String, alertId: String) = {
    alertsRepository.delete(requester, alertId)
  }
}


