package com.uptech.windalerts.core.alerts

import cats.Functor
import cats.data.EitherT
import cats.effect.Sync
import com.uptech.windalerts.Repos
import com.uptech.windalerts.core.alerts.domain.AlertT
import com.uptech.windalerts.domain.SurfsUpError
import com.uptech.windalerts.domain.domain.{AlertRequest}

class AlertsService[F[_]: Sync](repo: Repos[F]) {
  def save(alertRequest: AlertRequest, user: String): F[AlertT] = {
    repo.alertsRepository().save(alertRequest, user)
  }

  def updateT(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, SurfsUpError, AlertT] = repo.alertsRepository().updateT(requester, alertId, updateAlertRequest)

  def getAllForUser(user: String): F[AlertsT] = repo.alertsRepository().getAllForUser(user)


  def getAllForDayAndTimeRange()(implicit F: Functor[F]): EitherT[F, Exception, Seq[AlertT]] = {
    EitherT.liftF(repo.alertsRepository().getAllEnabled())
      .map(_.filter(_.isToBeAlertedNow()))
  }
  def deleteT(requester: String, alertId: String): EitherT[F, SurfsUpError, Unit] = {
    repo.alertsRepository().delete(requester, alertId)
  }
}


