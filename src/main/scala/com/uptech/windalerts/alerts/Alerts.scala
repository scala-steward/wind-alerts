package com.uptech.windalerts.alerts

import java.util.Calendar.{DAY_OF_WEEK, HOUR_OF_DAY}
import java.util.{Calendar, Date, TimeZone}

import cats.effect.IO
import com.google.cloud.firestore.WriteResult
import com.uptech.windalerts.domain.Domain.{Alert, AlertRequest}
import com.uptech.windalerts.domain.Errors.{OperationNotPermitted, RecordNotFound, WindAlertError}


trait Alerts extends Serializable {
  val alerts: Alerts.Service
}

object Alerts {

  trait Service {
    def getAllForDay: IO[Seq[Alert]]

    def save(alert: AlertRequest, user: String): IO[Alert]

    def getAllForUser(user: String): IO[com.uptech.windalerts.domain.Domain.Alerts]

    def delete(requester: String, alertId: String): IO[Either[RuntimeException, IO[WriteResult]]]

    def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): IO[Either[RuntimeException, IO[Alert]]]
  }

  class ServiceImpl(repo: AlertsRepository.Repository) extends Service {
    override def save(alertRequest: AlertRequest, user: String): IO[Alert] = repo.save(alertRequest, user)

    override def delete(requester: String, alertId: String): IO[Either[WindAlertError, IO[WriteResult]]] = {
      for {
        maybeAlert <- repo.getById(alertId)
        res <- IO(maybeAlert match {
          case Some(alert) => if (alert.owner == requester) Right(repo.delete(alertId)) else Left(OperationNotPermitted("Trying to access alert of another user"))
          case None => Left(RecordNotFound(s"Alert with id $alertId not found"))
        })

      } yield res

    }

    override def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): IO[Either[RuntimeException, IO[Alert]]] = repo.update(requester, alertId, updateAlertRequest)

    override def getAllForUser(user: String): IO[com.uptech.windalerts.domain.Domain.Alerts] = repo.getAllForUser(user)

    override def getAllForDay: IO[Seq[Alert]] =
      for {
        cal <- IO(Calendar.getInstance(TimeZone.getTimeZone("Australia/Sydney")))
        all <- repo.getAllForDay(cal.get(DAY_OF_WEEK))
        filtered <- IO(all.filter(_.isToBeAlertedAt(cal.get(HOUR_OF_DAY))))
      } yield filtered
  }

}