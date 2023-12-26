package com.uptech.windalerts.core.user

import cats.data.OptionT
import cats.mtl.Raise
import com.uptech.windalerts.core.{ UserNotFoundError}

trait UserRepository[F[_]] {
  def getByUserId(userId: String)(implicit FR: Raise[F, UserNotFoundError]): F[UserT]

  def getByUserIdOption(userId: String): OptionT[F, UserT]

  def getByEmailAndDeviceType(email: String, deviceType: String)(implicit FR: Raise[F, UserNotFoundError]): F[UserT]

  def create(user: UserT): F[UserT]

  def update(user: UserT)(implicit FR: Raise[F, UserNotFoundError]): F[UserT]

  def findTrialExpiredUsers(): F[Seq[UserT]]

  def findPremiumExpiredUsers(): F[Seq[UserT]]

  def findUsersWithNotificationsEnabledAndNotSnoozed(): F[Seq[UserT]]

  def deleteUserById(userId: String)(implicit FR: Raise[F, UserNotFoundError]): F[Long]

}
