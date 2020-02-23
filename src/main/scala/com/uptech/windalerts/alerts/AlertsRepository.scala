package com.uptech.windalerts.alerts

import java.util

import cats.effect.{ContextShift, IO}
import com.google.cloud.firestore
import com.google.cloud.firestore.{CollectionReference, Firestore, WriteResult}
import com.google.common.collect.Lists
import com.uptech.windalerts.domain.conversions.{j2sFuture, j2sm}
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.errors.{RecordNotFound, WindAlertError}
import com.uptech.windalerts.domain.{conversions, domain}
import org.log4s.getLogger

import scala.beans.BeanProperty
import scala.collection.JavaConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait AlertsRepository extends Serializable {
  val alerts: AlertsRepository.Repository
}

object AlertsRepository {

  trait Repository {
    def disableAllButOneAlerts(userId:String):IO[Seq[Alert]]

    def getById(id: String): IO[Option[Alert]]

    def getAllForDay(day: Int): IO[Seq[Alert]]

    def getAllForUser(user: String): IO[domain.Alerts]

    def save(alert: AlertRequest, user: String): IO[Alert]

    def delete(requester: String, id: String): IO[Either[WindAlertError, WriteResult]]

    def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): IO[Either[RuntimeException, IO[Alert]]]
  }

  class FirestoreAlertsRepository(db: Firestore)(implicit cs: ContextShift[IO]) extends AlertsRepository.Repository {
    private val logger = getLogger

    private val alerts: CollectionReference = db.collection("alerts")

    override def getById(id: String): IO[Option[Alert]] = {
      for {
        document <- IO.fromFuture(IO(j2sFuture(alerts.document(id).get())))
        maybeAlert <- IO {
          if (document.exists()) {
            val Alert(alert) = (document.getId, j2sm(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
            Some(alert)
          } else {
            None
          }
        }
      } yield maybeAlert

    }

    override def save(alertRequest: AlertRequest, user: String): IO[Alert] = {
      for {
        document <- IO.fromFuture(IO(j2s(alerts.add(toBean(Alert(alertRequest, user))))))
        alert <- IO(Alert(alertRequest, user).copy(id = document.getId))
      } yield alert
    }

    override def delete(requestor:String, alertId: String): IO[Either[WindAlertError, WriteResult]] = {
      for {
        maybeAlert <- getById(alertId)
        deleteOperationResult <- IO(maybeAlert match {
          case Some(_) => Right(alerts.document(alertId).delete().get())
          case None => Left(RecordNotFound(s"Alert with id $alertId not found"))
        })
      } yield deleteOperationResult
    }

    override def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): IO[Either[RuntimeException, IO[Alert]]] = {
      val alert = getById2(alertId)
      alert.map(alert => {
        if (alert.owner == requester) Right(update(alertId, Alert(updateAlertRequest, requester).copy(id = alertId))) else Left(new RuntimeException("Fail"))
      })
    }


    private def update(alertId: String, alert: Alert): IO[Alert] = {
      IO.fromFuture(IO(j2s(alerts.document(alertId).set(toBean(alert))).map(_ => alert)))
    }


    override def getAllForUser(user: String): IO[domain.Alerts] = {
      getAllByQuery(alerts.whereEqualTo("owner", user)).map(a => domain.Alerts(a))
    }

    override def getAllForDay(day: Int): IO[Seq[Alert]] = {
      for {
        all <- getAllByQuery(alerts.whereArrayContains("days", day).whereEqualTo("enabled", true))
      } yield all
    }

    private def getAllByQuery(query: firestore.Query) = {
      for {
        collection <- IO.fromFuture(IO(j2s(query.get())))
        _ <- IO(println("All " + collection))
        filtered <- IO(
          j2s(collection.getDocuments)
            .map(document => {
              val Alert(alert) = (document.getId, j2s(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
              alert
            }))
      } yield filtered
    }

    def toBean(alert: Alert): AlertBean = {
      val alertBean = new AlertBean(
        alert.owner,
        alert.beachId,
        new java.util.ArrayList(JavaConverters.asJavaCollection(alert.days)),
        new java.util.ArrayList(JavaConverters.asJavaCollection(alert.swellDirections)),
        new java.util.ArrayList(JavaConverters.asJavaCollection(alert.timeRanges)),
        alert.waveHeightFrom,
        alert.waveHeightTo,
        new java.util.ArrayList(JavaConverters.asJavaCollection(alert.windDirections)),
        new java.util.ArrayList(JavaConverters.asJavaCollection(alert.tideHeightStatuses)),
        alert.enabled,
        alert.timeZone)
      alertBean
    }

    private def getById2(id: String): IO[Alert] = {
      for {
        document <- IO.fromFuture(IO(j2s(alerts.document(id).get())))
        alert <- IO({
          val Alert(alert) = (document.getId, j2s(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
          alert
        })
      } yield alert
    }

    def j2s[A](inputList: util.List[A]) = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

    def j2s[K, V](map: util.Map[K, V]) = JavaConverters.mapAsScalaMap(map).toMap

    def j2s[A](javaFuture: util.concurrent.Future[A]): Future[A] = {
      Future(javaFuture.get())
    }

    override def disableAllButOneAlerts(userId:String):IO[Seq[Alert]] = {
     for {
        all <- getAllForUser(userId)
        updatedIOs <- IO({
          all.alerts.filter(_.enabled) match {
            case Seq() => List[IO[Alert]]()
            case Seq(only) => List[IO[Alert]](IO(only))
            case longSeq => longSeq.tail.map(a => update(a.id, a.copy(enabled = false)))
          }
        }

        )
        updatedAlerts <- conversions.toIOSeq(updatedIOs)
        _ <- IO(logger.error(s"Updated $updatedAlerts" ))
      } yield updatedAlerts
    }
  }

  class AlertBean(
                   @BeanProperty var owner: String,
                   @BeanProperty var beachId: Long,
                   @BeanProperty var days: java.util.List[Long],
                   @BeanProperty var swellDirections: java.util.List[String],
                   @BeanProperty var timeRanges: java.util.List[TimeRange],
                   @BeanProperty var waveHeightFrom: Double,
                   @BeanProperty var waveHeightTo: Double,
                   @BeanProperty var windDirections: java.util.List[String],
                   @BeanProperty var tideHeightStatuses: java.util.List[String],
                   @BeanProperty var enabled: Boolean,
                   @BeanProperty var timeZone: String = "Australia/Sydney") {}


}
