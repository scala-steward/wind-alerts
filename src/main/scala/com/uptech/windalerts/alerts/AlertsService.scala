package com.uptech.windalerts.alerts

import java.util.Calendar.{DAY_OF_WEEK, HOUR_OF_DAY, MINUTE}
import java.util.{Calendar, TimeZone}

import cats.data.EitherT
import cats.effect.IO
import com.uptech.windalerts.domain.domain.{AlertRequest, AlertT, AlertsT}
import com.uptech.windalerts.domain.errors.WindAlertError


trait AlertsService extends Serializable {
  val alerts: AlertsService.Service
}

object AlertsService {

  trait Service {

    def getAllForDayAndTimeRange: IO[Seq[AlertT]]

    def save(alert: AlertRequest, user: String): IO[AlertT]

    def getAllForUser(user: String): IO[com.uptech.windalerts.domain.domain.AlertsT]

    def deleteT(requester: String, alertId: String): EitherT[IO, WindAlertError, Unit]

    def updateT(requester: String, alertId: String, updateAlertRequest: AlertRequest) : EitherT[IO, WindAlertError, AlertT]

  }

  class ServiceImpl(repo: AlertsRepositoryT) extends Service {
    override def save(alertRequest: AlertRequest, user: String): IO[AlertT] = {
      repo.save(alertRequest, user)
    }

    override def updateT(requester: String, alertId: String, updateAlertRequest: AlertRequest) = repo.updateT(requester, alertId, updateAlertRequest)

    override def getAllForUser(user: String): IO[AlertsT] = repo.getAllForUser(user)

    override def getAllForDayAndTimeRange: IO[Seq[AlertT]] =
      for {
        cal <- IO(Calendar.getInstance(TimeZone.getTimeZone("Australia/Sydney")))
        all <- repo.getAllForDay(cal.get(DAY_OF_WEEK))
        filtered <- IO(all.filter(_.isToBeAlertedAt(getMinutes(cal))))
      } yield filtered

    override def deleteT(requester: String, alertId: String): EitherT[IO, WindAlertError, Unit] = {
      repo.delete(requester, alertId)
    }
  }

  private def getMinutes(cal: Calendar) = cal.get(HOUR_OF_DAY) * 60 + cal.get(MINUTE)
}