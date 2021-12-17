package com.uptech.windalerts.core.user

import cats.data.{EitherT, OptionT}
import com.uptech.windalerts.core.UserNotFoundError
import com.uptech.windalerts.infrastructure.endpoints.dtos.UserRequest

trait UserRepository[F[_]] {
  def getByUserId(userId: String): OptionT[F, UserT]

  def getByEmailAndDeviceType(email: String, deviceType: String): OptionT[F, UserT]

  def create(user: UserT): F[UserT]

  def update(user: UserT): OptionT[F, UserT]

  def findTrialExpiredUsers(): F[Seq[UserT]]

  def findPremiumExpiredUsers(): F[Seq[UserT]]

  def findUsersWithNotificationsEnabledAndNotSnoozed(): F[Seq[UserT]]
}
