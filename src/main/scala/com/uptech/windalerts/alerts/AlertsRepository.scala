package com.uptech.windalerts.alerts

import cats.data.EitherT
import com.uptech.windalerts.alerts.domain.AlertT
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.{SurfsUpError, domain}

trait AlertsRepositoryT[F[_]] {
  def disableAllButOneAlerts(userId: String): F[Seq[AlertT]]

  def getById(id: String): F[Option[AlertT]]

  def getAllEnabled(): F[Seq[AlertT]]

  def getAllForDay(day: Int, p:AlertT=>Boolean): F[Seq[AlertT]]

  def getAllForUser(user: String): F[domain.AlertsT]

  def save(alert: AlertRequest, user: String): F[AlertT]

  def delete(requester: String, id: String): EitherT[F, SurfsUpError, Unit]

  def updateT(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, SurfsUpError, AlertT]
}