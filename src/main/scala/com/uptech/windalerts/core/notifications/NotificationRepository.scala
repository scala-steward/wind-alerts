package com.uptech.windalerts.core.notifications

trait NotificationRepository[F[_]] {
  def create(alertId: String, userId: String, deviceToken: String, sentAt: Long): F[Notification]

  def countNotificationsInLastHour(userId: String): F[UserWithNotificationsCount]
}
