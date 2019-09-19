package com.uptech.windalerts.alerts

import java.util
import java.util.Date

import cats.effect.IO
import com.google.cloud.firestore.{Firestore, WriteResult}
import com.uptech.windalerts.domain.Domain.{Alert, AlertRequest}

import scala.collection.JavaConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.jmethods.catatumbo.{Entity, EntityManager}


trait Alerts extends Serializable {
  val alerts: Alerts.Service
}

object Alerts {

  trait Service {
    def getAllForDay: IO[Seq[Alert]]

    def save(alert: AlertRequest, user: String):IO[Alert]

    def getAllForUser(user:String) : IO[com.uptech.windalerts.domain.Domain.Alerts]

    def delete(requester:String, alertId:String):IO[Either[RuntimeException, IO[WriteResult]]]
  }

  class FireStoreBackedService(db:Firestore) extends Service {

    override def save(alertRequest: AlertRequest, user:String): IO[Alert] = {
      for {
        document <- IO.fromFuture(IO(j2s(db.collection("alerts").add(Alert(alertRequest, user).toBean))))
        alert <- IO({
          Alert(alertRequest, user).copy(id = document.getId)
        })
      } yield alert
    }

    override def delete(requester:String, alertId:String):IO[Either[RuntimeException, IO[WriteResult]]] = {
      val alert = getById(alertId)
      alert.map(alert => {
        if (alert.owner == requester) Right(delete(alertId)) else Left(new RuntimeException("Fail"))
      })
    }

    def delete(alertId:String): IO[WriteResult] = {
      IO.fromFuture(IO(j2s(db.collection("alerts").document(alertId).delete())))
    }

    def getById(id:String): IO[Alert] = {
      for {
        document <- IO.fromFuture(IO(j2s(db.collection("alerts").document(id).get())))
        alert <- IO({
          val Alert(alert) = (document.getId, j2s(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
          alert
        })
      } yield alert
    }

    override def getAllForUser(user:String): IO[com.uptech.windalerts.domain.Domain.Alerts] = {
      for {
        collection <- IO.fromFuture(IO(j2s(db.collection("alerts").whereEqualTo("owner", user).get())))
        filtered <- IO(com.uptech.windalerts.domain.Domain.Alerts(
          j2s(collection.getDocuments)
            .map(document => {
              val Alert(alert) = (document.getId, j2s(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
              alert
            })))
      } yield filtered
    }

    override def getAllForDay: IO[Seq[Alert]] = {
      for {
        date <- IO(new Date())
        collection <- IO.fromFuture(IO(j2s(db.collection("alerts").whereArrayContains("days", date.getDay).get())))
        filtered <- IO(
          j2s(collection.getDocuments)
            .map(document => {
              val Alert(alert) = (document.getId, j2s(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
              alert
            }).filter(_.isToBeAlertedAt(date.getHours)))
      } yield filtered

    }

    def j2s[A](inputList: util.List[A]) = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

    def j2s[K, V](map: util.Map[K, V]) = JavaConverters.mapAsScalaMap(map).toMap

    def j2s[A](javaFuture: util.concurrent.Future[A]): Future[A] = {
      Future(javaFuture.get())
    }

  }

}