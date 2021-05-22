package com.uptech.windalerts.core.notifications

import com.uptech.windalerts.core.notifications.NotificationsSender.NotificationDetails
import com.uptech.windalerts.core.user.UserId
import com.uptech.windalerts.core.beaches.domain._

import scala.util.Try


object NotificationsSender {
  case class NotificationDetails(beachId: BeachId, deviceToken: String, userId: UserId)
}

trait NotificationsSender[F[_]] {
  def send(nd: NotificationDetails): F[Try[String]]
}
