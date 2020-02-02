package com.uptech.windalerts.notifications

import cats.data.EitherT
import cats.effect.IO
import com.uptech.windalerts.domain.domain.{Notification, UserWithCount}

trait NotificationRepository {
  def create(notifications: Notification): IO[Notification]
  def countNotificationInLastHour(userId: String): EitherT[IO, Exception, UserWithCount]
}
