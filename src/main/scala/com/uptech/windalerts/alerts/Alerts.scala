package com.uptech.windalerts.alerts

import java.util
import java.util.Date

import cats.effect.IO
import com.google.cloud.firestore.Firestore
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

    def save(alert: AlertRequest, user: String):IO[String]

    def getAllForUser(user:String) : IO[com.uptech.windalerts.domain.Domain.Alerts]
  }

  class FireStoreBackedService(db:Firestore) extends Service {

    override def save(alert: AlertRequest, user:String): IO[String] = {
      val eventualReference = j2s(db.collection("alerts").add(Alert(alert, user).toBean))
      IO.fromFuture(IO(eventualReference.map(r=>r.getId)))
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