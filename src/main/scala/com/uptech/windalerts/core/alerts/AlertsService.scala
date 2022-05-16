package com.uptech.windalerts.core.alerts

import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import cats.{Applicative, Monad}
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.user.{UserId, UserType}
import com.uptech.windalerts.core.{AlertNotFoundError, OperationNotAllowed}

import scala.language.postfixOps

class AlertsService[F[_] : Sync](alertsRepository: AlertsRepository[F]) {
  def createAlert(userId: UserId, userType: UserType, alertRequest: AlertRequest)(implicit ONA: Raise[F, OperationNotAllowed]): F[Alert] = {
    for {
      _ <- if (userType.isPremiumUser()) Applicative[F].pure(()) else ONA.raise(OperationNotAllowed(s"Please subscribe to perform this action"))
      created <- alertsRepository.create(alertRequest, userId.id)
    } yield created
  }

  def update(alertId: String, userId: UserId, userType: UserType, alertRequest: AlertRequest)(implicit ONA: Raise[F, OperationNotAllowed], ANF: Raise[F, AlertNotFoundError]): F[Alert] = {
    for {
      _ <- authorizeAlertEditRequest(userId, userType, alertId, alertRequest)
      updated <- alertsRepository.update(userId.id, alertId, alertRequest)
    } yield updated
  }

  def authorizeAlertEditRequest(userId: UserId, userType: UserType, alertId: String, alertRequest: AlertRequest)(implicit FR: Raise[F, OperationNotAllowed], ONA: Raise[F, AlertNotFoundError], A: Applicative[F]): F[Unit] = {
    if (userType.isPremiumUser())
      A.pure(())
    else {
      authorizeAlertEditNonPremiumUser(userId, alertId, alertRequest)
    }
  }

  private def authorizeAlertEditNonPremiumUser(userId: UserId, alertId: String, alertRequest: AlertRequest)(implicit M: Monad[F], ONA: Raise[F, OperationNotAllowed], ANF: Raise[F, AlertNotFoundError], A: Applicative[F]): F[Unit] = {
    for {
      firstAlert <- getFirstAlert(userId)
      canEdit <- if (checkForNonPremiumUser(alertId, firstAlert, alertRequest)) A.pure(()) else ONA.raise(OperationNotAllowed(s"Please subscribe to perform this action"))
    } yield canEdit
  }

  private def getFirstAlert(userId: UserId)(implicit FR: Raise[F, OperationNotAllowed]) = {
    alertsRepository.getFirstAlert(userId.id).getOrElseF(FR.raise(OperationNotAllowed("Please subscribe to perform this action")))
  }

  def checkForNonPremiumUser(alertId: String, alert: Alert, alertRequest: AlertRequest)(implicit FR: Raise[F, AlertNotFoundError]) = {
    if (alert.id != alertId) {
      false
    } else {
      alert.allFieldExceptStatusAreSame(alertRequest)
    }
  }

  def getAllForUser(user: String): F[Seq[Alert]] = alertsRepository.getAllForUser(user)

  def delete(requester: String, alertId: String)(implicit FR: Raise[F, AlertNotFoundError]) = {
    alertsRepository.delete(requester, alertId)
  }
}


