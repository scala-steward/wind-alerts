package com.uptech.windalerts.infrastructure.notifications

import cats.effect.{Async, Sync}
import com.google.firebase.messaging.Message._
import com.google.firebase.messaging._
import com.uptech.windalerts.core.notifications.NotificationsSender
import com.uptech.windalerts.core.notifications.NotificationsSender.NotificationDetails
import com.uptech.windalerts.domain.beaches.Beach
import com.uptech.windalerts.domain.config.AppConfig

import scala.util.Try

class FirebaseBasedNotificationsSender[F[_] : Sync]
(firebaseMessaging: FirebaseMessaging, beaches: Map[Long, Beach], config: AppConfig)(implicit F: Async[F]) extends NotificationsSender[F]{

  override def send(nd:NotificationDetails):F[Try[String]] = {
    F.delay {
      prepareMessage(nd).flatMap(message => Try {
        firebaseMessaging.send(message)
      })
    }
  }


  private def prepareMessage(nd: NotificationDetails)(implicit F: Async[F]) = {
    Try{
    val beachName = beaches(nd.beachId.id).location
    val title = config.surfsUp.notifications.title.replaceAll("BEACH_NAME", beachName)
    val body = config.surfsUp.notifications.body
    builder().setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
      .setWebpushConfig(WebpushConfig.builder().putHeader("Urgency", "high").build())
      .putData("beachId", s"${nd.beachId.id}")
      .setNotification(new Notification(title, body))
      .setToken(nd.deviceToken)
      .setApnsConfig(ApnsConfig.builder()
        .setAps(Aps.builder().build())
        .putHeader("apns-priority", "10").build())
      .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
      .build()
    }
  }
}
