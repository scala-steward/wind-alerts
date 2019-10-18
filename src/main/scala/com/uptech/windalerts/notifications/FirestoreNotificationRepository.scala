package com.uptech.windalerts.notifications

import java.util

import cats.effect.{ContextShift, IO}
import com.google.cloud.firestore
import com.google.cloud.firestore.{CollectionReference, Firestore, QueryDocumentSnapshot}
import com.uptech.windalerts.domain.conversions.{j2sFuture, j2sMap, j2sm}
import com.uptech.windalerts.domain.{FirestoreOps, domain}
import com.uptech.windalerts.domain.domain.{Credentials, Notification}

import scala.beans.BeanProperty

class FirestoreNotificationRepository(db: Firestore, dbops:FirestoreOps)(implicit cs: ContextShift[IO]) extends NotificationRepository {
  private val collection: CollectionReference = db.collection("notifications")

  override def create(notification: domain.Notification): IO[domain.Notification] = {
    for {
      document <- IO.fromFuture(IO(j2sFuture(collection.add(toBean(notification)))))
      saved <- IO(notification.copy(id = Some(document.getId)))
    } yield saved
  }

  def toBean(notification: domain.Notification) = {
    new NotificationBean(notification.alertId, notification.deviceToken, notification.title, notification.body, notification.sentAt)
  }

  override def countNotificationInLastHour(alertId:String) = {
    for {
      all <- getByQuery(collection.whereEqualTo("alertId", alertId).whereGreaterThan("sentAt", System.currentTimeMillis() - (60 * 60 * 1000)))
    } yield all.size
  }

  private def getByQuery(query: firestore.Query) = {
    val mf: QueryDocumentSnapshot => Notification = document => {
      val Notification(notification) = (document.getId, j2sm(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
      notification
    }
    dbops.getByQuery(query, mf)
  }
}


class NotificationBean(
                        @BeanProperty var alertId: String,
                        @BeanProperty var deviceToken: String,
                        @BeanProperty var title: String,
                        @BeanProperty var body: String,
                        @BeanProperty var sentAt: Long
                      ) {}
