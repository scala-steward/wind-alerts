package com.uptech.windalerts.notifications

import cats.data.EitherT
import com.uptech.windalerts.domain.domain.{Notification, UserWithCount}

trait NotificationRepository[F[_]] {
  def create(notifications: Notification): F[Notification]
  def countNotificationInLastHour(userId: String): EitherT[F, Exception, UserWithCount]
}
