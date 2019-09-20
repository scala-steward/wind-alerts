package com.uptech.windalerts.alerts

import java.util.Date

import cats.effect.IO
import com.google.cloud.firestore.WriteResult
import com.uptech.windalerts.domain.Domain.{Alert, AlertRequest}


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

    override def delete(requester: String, alertId: String): IO[Either[RuntimeException, IO[WriteResult]]] = repo.delete(requester, alertId)

    override def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): IO[Either[RuntimeException, IO[Alert]]] = repo.update(requester, alertId, updateAlertRequest)

    override def getAllForUser(user: String): IO[com.uptech.windalerts.domain.Domain.Alerts] = repo.getAllForUser(user)

    override def getAllForDay: IO[Seq[Alert]] =
      for {
        date <- IO(new Date())
          all <-repo.getAllForDay(new Date().getDay)
          filtered <- IO(all.filter(_.isToBeAlertedAt(date.getHours)))
       } yield filtered
  }

}