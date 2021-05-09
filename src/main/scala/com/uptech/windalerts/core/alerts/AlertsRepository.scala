package com.uptech.windalerts.core.alerts

import cats.data.EitherT
import com.uptech.windalerts.core.AlertNotFoundError
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.domain.domain._

trait AlertsRepositoryT[F[_]] {
  def disableAllButOneAlerts(userId: String): F[Seq[Alert]]

  def getAllEnabled(): F[Seq[Alert]]

  def getAllForUser(user: String): F[Alerts]

  def save(alert: AlertRequest, user: String): F[Alert]

  def delete(requester: String, id: String):  EitherT[F, AlertNotFoundError, Unit]

  def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, AlertNotFoundError, Alert]
}