package com.uptech.windalerts.alerts

import java.util.Calendar.{DAY_OF_WEEK, HOUR_OF_DAY, MINUTE}
import java.util.{Calendar, TimeZone}

import cats.data.EitherT
import cats.effect.Sync
import com.uptech.windalerts.domain.domain.{AlertRequest, AlertT, AlertsT}
import com.uptech.windalerts.domain.errors.WindAlertError

class AlertsService[F[_]: Sync](repo: AlertsRepositoryT[F]) {
  def save(alertRequest: AlertRequest, user: String): F[AlertT] = {
    repo.save(alertRequest, user)
  }

  def updateT(requester: String, alertId: String, updateAlertRequest: AlertRequest) = repo.updateT(requester, alertId, updateAlertRequest)

  def getAllForUser(user: String): F[AlertsT] = repo.getAllForUser(user)

  def getAllForDayAndTimeRange()(implicit F: Sync[F]): F[Seq[AlertT]] = {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Australia/Sydney"))
    repo.getAllForDay(cal.get(DAY_OF_WEEK), _.isToBeAlertedAt(getMinutes(cal)))
  }

  def deleteT(requester: String, alertId: String): EitherT[F, WindAlertError, Unit] = {
    repo.delete(requester, alertId)
  }
  private def getMinutes(cal: Calendar) = cal.get(HOUR_OF_DAY) * 60 + cal.get(MINUTE)
}