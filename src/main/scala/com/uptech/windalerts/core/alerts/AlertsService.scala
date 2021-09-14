package com.uptech.windalerts.core.alerts

import cats.Bifunctor.ops.toAllBifunctorOps
import cats.Functor
import cats.data.EitherT
import cats.effect.Sync
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.user.{UserId, UserRepository, UserRolesService, UserT, UserType}
import com.uptech.windalerts.core.{AlertNotFoundError, OperationNotAllowed, SurfsUpError, UserNotFoundError}
import com.uptech.windalerts.infrastructure.endpoints.dtos.{AlertDTO, AlertRequest}

class AlertsService[F[_] : Sync](alertsRepository: AlertsRepository[F], userRepository: UserRepository[F]) {
  def createAlert(u: UserId, r: AlertRequest) = {
    for {
      dbUser <- userRepository.getByUserId(u.id).toRight(UserNotFoundError())
      _ <- EitherT.fromEither(Either.cond(UserType(dbUser.userType).isPremiumUser(), (), OperationNotAllowed(s"Please subscribe to perform this action")))
      saved <- save(u, r)
    } yield saved
  }

  private def save(u: UserId, r: AlertRequest): cats.data.EitherT[F, SurfsUpError, AlertDTO] = {
    EitherT.liftF(alertsRepository.save(r, u.id)).map(_.asDTO())
  }

  def update(alertId: String, u: UserId, r: AlertRequest): EitherT[F, SurfsUpError, AlertDTO] = {
    for {
      dbUser <- userRepository.getByUserId(u.id).toRight(UserNotFoundError())
      _ <- authorizeAlertEditRequest(dbUser, alertId, r).leftWiden[SurfsUpError]
      saved <- update(u.id, alertId, r).map(_.asDTO()).leftWiden[SurfsUpError]
    } yield saved
  }


  def authorizeAlertEditRequest(user: UserT, alertId: String, alertRequest: AlertRequest): EitherT[F, OperationNotAllowed, UserT] = {
    if (UserType(user.userType).isPremiumUser())
      EitherT.pure(user)
    else {
      for {
        first <- alertsRepository.getFirstAlert(user._id.toHexString).toRight(OperationNotAllowed(s"Please subscribe to perform this action"))
        canEdit <- EitherT.fromEither(Either.cond(checkForNonPremiumUser(alertId, first, alertRequest), user, OperationNotAllowed(s"Please subscribe to perform this action")))
      } yield canEdit
    }
  }

  def checkForNonPremiumUser(alertId: String, alert: Alert, alertRequest: AlertRequest) = {
    if (alert._id.toHexString != alertId) {
      false
    } else {
      alert.allFieldExceptStatusAreSame(alertRequest)
    }
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


