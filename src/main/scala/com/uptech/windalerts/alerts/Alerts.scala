package com.uptech.windalerts.alerts

import java.util
import java.util.Date

import cats.effect.IO
import com.google.cloud.firestore.Firestore
import com.uptech.windalerts.domain.Domain.Alert

import scala.collection.JavaConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Alerts extends Serializable {
  val alerts: Alerts.Service
}

object Alerts {

  trait Service {
    def getAllForDay: IO[Seq[Alert]]

    def save(alert: Alert):IO[String]
  }

  class FireStoreBackedService(db:Firestore) extends Service {

    override def save(alert: Alert): IO[String] = {
      val eventualReference = j2s(db.collection("alerts").add(alert.toBean))
      IO.fromFuture(IO(eventualReference.map(r=>r.getId)))
    }

    override def getAllForDay: IO[Seq[Alert]] = {
      for {
        date <- IO(new Date())
        collection <- IO.fromFuture(IO(j2s(db.collection("alerts").whereArrayContains("days", date.getDay).get())))
        filtered <- IO(
          j2s(collection.getDocuments)
            .map(document => {
              val Alert(alert) = j2s(document.getData).asInstanceOf[Map[String, java.util.HashMap[String, String]]]
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