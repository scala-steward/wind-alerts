package com.uptech.windalerts.users


import cats.data.{EitherT, OptionT}
import com.uptech.windalerts.domain.SurfsUpError
import com.uptech.windalerts.domain.domain.UserT

trait UserRepositoryAlgebra[F[_]] {

  def getByUserIdEitherT(userId: String): EitherT[F, Exception, UserT]

  def getByUserId(userId: String): F[Option[UserT]]

  def getByEmailAndDeviceType(email: String, deviceType: String): F[Option[UserT]]

  def create(user: UserT): F[UserT]

  def update(user: UserT): OptionT[F, UserT]

  def updateDeviceToken(userId: String, deviceToken: String): OptionT[F, Unit]

  def findTrialExpiredUsers(): EitherT[F, SurfsUpError, Seq[UserT]]

  def findAndroidPremiumExpiredUsers(): EitherT[F, SurfsUpError, Seq[UserT]]

  def findApplePremiumExpiredUsers(): EitherT[F, SurfsUpError, Seq[UserT]]
}
