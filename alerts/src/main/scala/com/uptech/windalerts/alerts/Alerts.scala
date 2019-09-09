package com.uptech.windalerts.alerts

import java.util
import java.util.Date

import cats.effect.IO
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.domain.Domain.Alert

import scala.collection.JavaConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

trait Alerts extends Serializable {
  val alerts: Alerts.Service
}

object Alerts {

  trait Service {
    val getAllForDay: IO[Seq[Alert]]
  }

  object FireStoreBackedService extends Service {
    val credentials = GoogleCredentials.getApplicationDefault
    val options = new FirebaseOptions.Builder().setCredentials(credentials).setProjectId("wind-alerts-staging").build
    FirebaseApp.initializeApp(options)
    val db = FirestoreClient.getFirestore

    override val getAllForDay: IO[Seq[Alert]] = {

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