package com.uptech.windalerts.infrastructure.notifications

import cats.Applicative
import cats.effect.{Async, Sync}
import com.google.firebase.messaging.Message._
import com.google.firebase.messaging._
import com.uptech.windalerts.config.beaches.Beach
import com.uptech.windalerts.config.config.Notifications
import com.uptech.windalerts.core.notifications.NotificationsSender
import com.uptech.windalerts.core.notifications.NotificationsSender.NotificationDetails

class FirebaseBasedNotificationsSender[F[_] : Sync]
(firebaseMessaging: FirebaseMessaging, beaches: Map[Long, Beach], config: Notifications)(implicit F: Async[F]) extends NotificationsSender[F] {


  override def send(nd: NotificationDetails): F[String] = {
    Applicative[F].pure(firebaseMessaging.send(prepareMessage(nd)))
  }


  private def prepareMessage(nd: NotificationDetails)(implicit F: Async[F]) = {
    val beachName = beaches(nd.beachId.id).location
    val title = config.title.replaceAll("BEACH_NAME", beachName)
    val body = config.body
    builder().setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
      .setWebpushConfig(WebpushConfig.builder().putHeader("Urgency", "high").build())
      .putData("beachId", s"${nd.beachId.id}")
      .setNotification(Notification.builder().setTitle(title).setBody(body).build())
      .setToken(nd.deviceToken)
      .setApnsConfig(ApnsConfig.builder()
        .setAps(Aps.builder().setSound("default").build())
        .putHeader("apns-priority", "10").build())
      .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
      .build()
  }

}
