package com.uptech.windalerts.infrastructure.notifications

import cats.data.EitherT
import cats.effect.{Async, Sync}
import com.google.firebase.messaging.Message._
import com.google.firebase.messaging._
import com.uptech.windalerts.core.notifications.NotificationsSender
import com.uptech.windalerts.core.notifications.NotificationsSender.NotificationDetails
import com.uptech.windalerts.config.beaches.Beach
import com.uptech.windalerts.config.config.{AppConfig, Notifications}
import com.uptech.windalerts.logger

import scala.util.Try

class FirebaseBasedNotificationsSender[F[_] : Sync]
(firebaseMessaging: FirebaseMessaging, beaches: Map[Long, Beach], config: Notifications)(implicit F: Async[F]) extends NotificationsSender[F] {

  override def send(nd: NotificationDetails): EitherT[F, Throwable, String] = {
    EitherT.fromEither({
      prepareMessage(nd).flatMap(message => Try {
        firebaseMessaging.send(message)
      }
      ).toEither
    }).leftMap(e => {
      logger.warn(s"Error while sending notification to user ${nd.userId} for beach ${nd.beachId} ", e)
      e
    }).map(status=>{
      logger.warn(s"Sent notification to user ${nd.userId} for beach ${nd.beachId}, status ${status} ")
      status
    })
  }


  private def prepareMessage(nd: NotificationDetails)(implicit F: Async[F]) = {
    Try {
      val beachName = beaches(nd.beachId.id).location
      val title = config.title.replaceAll("BEACH_NAME", beachName)
      val body = config.body
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
