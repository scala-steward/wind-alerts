package com.uptech.windalerts.alerts

import java.util.Calendar.{DAY_OF_WEEK, HOUR_OF_DAY}
import java.util.{Calendar, TimeZone}

import cats.effect.IO
import com.google.cloud.firestore.WriteResult
import com.uptech.windalerts.domain.errors.WindAlertError
import com.uptech.windalerts.domain.domain.{Alert, AlertRequest, Alerts}


trait AlertsService extends Serializable {
  val alerts: AlertsService.Service
}

object AlertsService {

  trait Service {
    def getAllForDay: IO[Seq[Alert]]

    def save(alert: AlertRequest, user: String): IO[Alert]

    def getAllForUser(user: String): IO[com.uptech.windalerts.domain.domain.Alerts]

    def delete(requester: String, alertId: String): IO[Either[WindAlertError, WriteResult]]

    def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): IO[Either[RuntimeException, IO[Alert]]]
  }

  class ServiceImpl(repo: AlertsRepository.Repository) extends Service {
    override def save(alertRequest: AlertRequest, user: String): IO[Alert] = repo.save(alertRequest, user)

    override def delete(requester: String, alertId: String): IO[Either[WindAlertError, WriteResult]] = {
      repo.delete(requester, alertId)
    }

    override def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): IO[Either[RuntimeException, IO[Alert]]] = repo.update(requester, alertId, updateAlertRequest)

    override def getAllForUser(user: String): IO[Alerts] = repo.getAllForUser(user)

    override def getAllForDay: IO[Seq[Alert]] =
      for {
        cal <- IO(Calendar.getInstance(TimeZone.getTimeZone("Australia/Sydney")))
        all <- repo.getAllForDay(cal.get(DAY_OF_WEEK))
        filtered <- IO(all.filter(_.isToBeAlertedAt(cal.get(HOUR_OF_DAY))))
      } yield filtered
  }

}