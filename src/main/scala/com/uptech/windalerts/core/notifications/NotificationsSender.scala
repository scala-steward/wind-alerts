package com.uptech.windalerts.core.notifications

import com.uptech.windalerts.core.notifications.NotificationsSender.NotificationDetails
import com.uptech.windalerts.domain.domain.{BeachId, UserId}

import scala.util.Try


object NotificationsSender {
  case class NotificationDetails(beachId: BeachId, deviceToken: String, userId: UserId)
}

trait NotificationsSender[F[_]] {
  def send(nd: NotificationDetails): F[Try[String]]
}
