package com.uptech.windalerts.core.alerts

import cats.Bifunctor.ops.toAllBifunctorOps
import cats.data.EitherT
import cats.effect.Sync
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.user.{UserId, UserType}
import com.uptech.windalerts.core.{AlertNotFoundError, OperationNotAllowed, SurfsUpError}

class AlertsService[F[_] : Sync](alertsRepository: AlertsRepository[F]) {
  def createAlert(u: UserId, userType: UserType, r: AlertRequest):EitherT[F, OperationNotAllowed, Alert] = {
    for {
      _ <- EitherT.cond[F](userType.isPremiumUser(), (), OperationNotAllowed(s"Please subscribe to perform this action"))
      saved <- EitherT.liftF(alertsRepository.create(r, u.id))
    } yield saved
  }

  def update(alertId: String, u: UserId, userType: UserType, r: AlertRequest): EitherT[F, SurfsUpError, Alert] = {
    for {
      _ <- authorizeAlertEditRequest(u, userType, alertId, r).leftWiden[SurfsUpError]
      saved <- update(u.id, alertId, r).leftWiden[SurfsUpError]
    } yield saved
  }

  def authorizeAlertEditRequest(userId: UserId, userType: UserType, alertId: String, alertRequest: AlertRequest): EitherT[F, OperationNotAllowed, Unit] = {
    if (userType.isPremiumUser())
      EitherT.pure(())
    else {
      authorizeAlertEditNonPremiumUser(userId, alertId, alertRequest)
    }
  }

  private def authorizeAlertEditNonPremiumUser(userId: UserId, alertId: String, alertRequest: AlertRequest) = {
    for {
      firstAlert <- alertsRepository.getFirstAlert(userId.id).toRight(OperationNotAllowed(s"Please subscribe to perform this action"))
      canEdit <- EitherT.fromEither(Either.cond(checkForNonPremiumUser(alertId, firstAlert, alertRequest), (), OperationNotAllowed(s"Please subscribe to perform this action")))
    } yield canEdit
  }

  def checkForNonPremiumUser(alertId: String, alert: Alert, alertRequest: AlertRequest) = {
    if (alert.id != alertId) {
      false
    } else {
      alert.allFieldExceptStatusAreSame(alertRequest)
    }
  }

  def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, AlertNotFoundError, Alert] = alertsRepository.update(requester, alertId, updateAlertRequest)

  def getAllForUser(user: String): F[Seq[Alert]] = alertsRepository.getAllForUser(user)

  def delete(requester: String, alertId: String) = {
    alertsRepository.delete(requester, alertId)
  }
}


