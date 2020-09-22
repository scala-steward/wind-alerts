package com.uptech.windalerts.alerts

import java.util.Calendar.{DAY_OF_WEEK, HOUR_OF_DAY, MINUTE}
import java.util.{Calendar, TimeZone}

import cats.data.EitherT
import cats.effect.Sync
import com.uptech.windalerts.Repos
import com.uptech.windalerts.alerts.domain.AlertT
import com.uptech.windalerts.domain.SurfsUpError
import com.uptech.windalerts.domain.domain.{AlertRequest, AlertsT}

class AlertsService[F[_]: Sync](repo: Repos[F]) {
  def save(alertRequest: AlertRequest, user: String): F[AlertT] = {
    repo.alertsRepository().save(alertRequest, user)
  }

  def updateT(requester: String, alertId: String, updateAlertRequest: AlertRequest) = repo.alertsRepository().updateT(requester, alertId, updateAlertRequest)

  def getAllForUser(user: String): F[AlertsT] = repo.alertsRepository().getAllForUser(user)

  def getAllForDayAndTimeRange()(implicit F: Sync[F]): F[Seq[AlertT]] = {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Australia/Sydney"))
    repo.alertsRepository().getAllForDay(cal.get(DAY_OF_WEEK), _.isToBeAlertedAt(getMinutes(cal)))
  }

  def deleteT(requester: String, alertId: String): EitherT[F, SurfsUpError, Unit] = {
    repo.alertsRepository()delete(requester, alertId)
  }
  private def getMinutes(cal: Calendar) = cal.get(HOUR_OF_DAY) * 60 + cal.get(MINUTE)
}


