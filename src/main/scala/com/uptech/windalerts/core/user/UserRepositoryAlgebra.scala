package com.uptech.windalerts.core.user

import cats.data.{EitherT, OptionT}
import com.uptech.windalerts.domain.SurfsUpError

trait UserRepositoryAlgebra[F[_]] {

  def getByUserIdEitherT(userId: String): EitherT[F, Exception, UserT]

  def getByUserId(userId: String): OptionT[F, UserT]

  def getByEmailAndDeviceType(email: String, deviceType: String): OptionT[F, UserT]

  def create(user: UserT): F[UserT]

  def update(user: UserT): OptionT[F, UserT]

  def updateDeviceToken(userId: String, deviceToken: String): OptionT[F, Unit]

  def findTrialExpiredUsers(): EitherT[F, SurfsUpError, Seq[UserT]]

  def findAndroidPremiumExpiredUsers(): EitherT[F, SurfsUpError, Seq[UserT]]

  def findApplePremiumExpiredUsers(): EitherT[F, SurfsUpError, Seq[UserT]]
}
