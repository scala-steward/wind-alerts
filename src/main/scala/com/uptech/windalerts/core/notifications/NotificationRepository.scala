package com.uptech.windalerts.core.notifications

trait NotificationRepository[F[_]] {
  def create(notifications: Notification): F[Notification]
  def countNotificationInLastHour(userId: String): F[UserWithNotificationsCount]
}
