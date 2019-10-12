package com.uptech.windalerts.notifications

import cats.effect.IO
import com.uptech.windalerts.domain.domain.Notification

trait NotificationRepository {
  def create(notifications: Notification): IO[Notification]
  def countNotificationInLastHour(userId: String): IO[Int]
}
