package com.uptech.windalerts.core.alerts

import cats.Bifunctor.ops.toAllBifunctorOps
import cats.data.EitherT
import cats.effect.Sync
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.user.{UserId, UserType}
import com.uptech.windalerts.core.{AlertNotFoundError, OperationNotAllowed, SurfsUpError}

class AlertsService[F[_] : Sync](alertsRepository: AlertsRepository[F]) {
  def createAlert(userId: UserId, userType: UserType, alertRequest: AlertRequest): EitherT[F, OperationNotAllowed, Alert] = {
    for {
      _ <- EitherT.cond[F](userType.isPremiumUser(), (), OperationNotAllowed(s"Please subscribe to perform this action"))
      saved <- EitherT.liftF(alertsRepository.create(alertRequest, userId.id))
    } yield saved
  }

  def update(alertId: String, userId: UserId, userType: UserType, alertRequest: AlertRequest): EitherT[F, SurfsUpError, Alert] = {
    for {
      _ <- authorizeAlertEditRequest(userId, userType, alertId, alertRequest).leftWiden[SurfsUpError]
      updated <- alertsRepository.update(userId.id, alertId, alertRequest).leftWiden[SurfsUpError]
    } yield updated
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

  def getAllForUser(user: String): F[Seq[Alert]] = alertsRepository.getAllForUser(user)

  def delete(requester: String, alertId: String) = {
    alertsRepository.delete(requester, alertId)
  }
}


