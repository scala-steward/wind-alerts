package com.uptech.windalerts.core.user

import cats.data.OptionT

trait UserRepository[F[_]] {
  def getByUserId(userId: String): OptionT[F, UserT]

  def getByEmailAndDeviceType(email: String, deviceType: String): OptionT[F, UserT]

  def create(user: UserT): F[UserT]

  def update(user: UserT): OptionT[F, UserT]

  def findTrialExpiredUsers(): F[Seq[UserT]]

  def findPremiumExpiredUsers(): F[Seq[UserT]]

  def findUsersWithNotificationsEnabledAndNotSnoozed(): F[Seq[UserT]]
}
