package com.uptech.windalerts.core.alerts

import cats.data.{EitherT, OptionT}
import com.uptech.windalerts.core.AlertNotFoundError
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.infrastructure.endpoints.dtos._

trait AlertsRepository[F[_]] {
  def disableAllButFirstAlerts(userId: String): F[Seq[Alert]]

  def getFirstAlert(userId: String): OptionT[F, Alert]

  def getAllEnabled(): F[Seq[Alert]]

  def getAllForUser(user: String): F[Seq[Alert]]

  def getAllEnabledForUser(user: String): F[Seq[Alert]]

  def save(alert: AlertRequest, user: String): F[Alert]

  def delete(requester: String, id: String):  EitherT[F, AlertNotFoundError, Unit]

  def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, AlertNotFoundError, Alert]
}