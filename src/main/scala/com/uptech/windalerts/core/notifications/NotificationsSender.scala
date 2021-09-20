package com.uptech.windalerts.core.notifications

import cats.data.EitherT
import com.uptech.windalerts.core.notifications.NotificationsSender.NotificationDetails
import com.uptech.windalerts.core.user.UserId
import com.uptech.windalerts.core.beaches.domain._

import scala.util.Try


object NotificationsSender {
  case class NotificationDetails(beachId: BeachId, deviceToken: String, userId: UserId)
}

trait NotificationsSender[F[_]] {
  def send(nd: NotificationDetails): EitherT[F, Throwable, String]
}
