package com.uptech.windalerts.core.alerts

import cats.data.EitherT
import com.uptech.windalerts.core.AlertNotFoundError
import com.uptech.windalerts.core.alerts.domain.AlertT
import com.uptech.windalerts.domain.domain._

trait AlertsRepositoryT[F[_]] {
  def disableAllButOneAlerts(userId: String): F[Seq[AlertT]]

  def getAllEnabled(): F[Seq[AlertT]]

  def getAllForUser(user: String): F[AlertsT]

  def save(alert: AlertRequest, user: String): F[AlertT]

  def delete(requester: String, id: String):  EitherT[F, AlertNotFoundError, Unit]

  def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, AlertNotFoundError, AlertT]
}