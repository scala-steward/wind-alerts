package com.uptech.windalerts.core.user

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.core.alerts.AlertsRepository
import com.uptech.windalerts.core.otp.OtpRepository
import com.uptech.windalerts.core.refresh.tokens.UserSessionRepository
import com.uptech.windalerts.core.social.SocialPlatformType
import com.uptech.windalerts.core.social.subscriptions.SocialPlatformSubscriptionsService
import com.uptech.windalerts.core.user.UserType.{Premium, PremiumExpired, Trial}
import com.uptech.windalerts.core.{OperationNotAllowed, SurfsUpError, UnknownError, UserNotFoundError}
import com.uptech.windalerts.infrastructure.endpoints.codecs._
import com.uptech.windalerts.infrastructure.endpoints.dtos._
import com.uptech.windalerts.infrastructure.social.SocialPlatformTypes.{Apple, Google}
import com.uptech.windalerts.infrastructure.social.subscriptions.PurchaseTokenRepository
import io.circe.parser.parse

class UserRolesService[F[_] : Sync](alertsRepository: AlertsRepository[F], userRepository: UserRepository[F], otpRepository: OtpRepository[F], socialPlatformSubscriptionsService: SocialPlatformSubscriptionsService[F]) {
  def updateTrialUsers() = {
    for {
      users <- EitherT.right(userRepository.findTrialExpiredUsers())
      _ <- makeUsersTrialExpired(users)
    } yield ()
  }

  private def makeUsersTrialExpired(users: Seq[UserT]): EitherT[F, UserNotFoundError, List[UserT]] = {
    users.map(user => makeUserTrialExpired(user)).toList.sequence
  }


  def updateSubscribedUsers() = {
    for {
      users <- EitherT.right(userRepository.findPremiumExpiredUsers())
      _ <- users.map(user => updateSubscribedUser(user)).toList.sequence
    } yield ()
  }

  private def updateSubscribedUser(user: UserT): EitherT[F, SurfsUpError, Unit] = {

    for {
      purchase <- socialPlatformSubscriptionsService.find(user._id.toHexString, user.deviceType)
      _ <- updateSubscribedUserRole(user, purchase.startTimeMillis, purchase.expiryTimeMillis).leftWiden[SurfsUpError]
    } yield ()
  }


  def updateSubscribedUserRole(user: UserT, startTime: Long, expiryTime: Long) = {
    for {
      userWithUpdatedRole <- {
        if (expiryTime > System.currentTimeMillis()) {
          makeUserPremium(user, startTime, expiryTime)
        } else {
          makeUserPremiumExpired(user)
        }
      }
    } yield userWithUpdatedRole
  }

  def authorizePremiumUsers(user: UserT): EitherT[F, SurfsUpError, UserT] = {
    EitherT.fromEither(if (UserType(user.userType) == UserType.Premium || UserType(user.userType) == UserType.Trial) {
      Right(user)
    } else {
      Left(OperationNotAllowed(s"Please subscribe to perform this action"))
    })
  }


  def handleUpdate(socialPlatformType:SocialPlatformType, purchaseToken: String): EitherT[F, SurfsUpError, UserT] = {
    for {
      purchase <- socialPlatformSubscriptionsService.getLatestForToken(socialPlatformType, purchaseToken)
      user <- userRepository.getByUserId(purchase.userId.id).toRight(UserNotFoundError())
      updatedUser <- updateSubscribedUserRole(user, purchase.subscriptionPurchase.startTimeMillis, purchase.subscriptionPurchase.expiryTimeMillis).leftWiden[SurfsUpError]
    } yield updatedUser
  }

  def verifyEmail(user: UserId, request: OTP): EitherT[F, SurfsUpError, UserDTO] = {
    for {
      _ <- otpRepository.exists(request.otp, user.id)
      user <- userRepository.getByUserId(user.id).toRight(UserNotFoundError())
      updateResult <- makeUserTrial(user).map(_.asDTO()).leftWiden[SurfsUpError]
    } yield updateResult
  }

  def updateUserPurchase(u: UserId) = {
    for {
      dbUser <- userRepository.getByUserId(u.id).toRight(UserNotFoundError()).leftWiden[SurfsUpError]
      purchase <- socialPlatformSubscriptionsService.find(u.id, dbUser.deviceType)
      userWithUpdatedRole <- updateSubscribedUserRole(dbUser, purchase.startTimeMillis, purchase.expiryTimeMillis).map(_.asDTO()).leftWiden[SurfsUpError]
    } yield userWithUpdatedRole
  }

  def makeUserTrial(user: UserT): EitherT[F, UserNotFoundError, UserT] = {
    update(user.copy(startTrialAt = System.currentTimeMillis(), endTrialAt = System.currentTimeMillis() + (30L * 24L * 60L * 60L * 1000L), userType = Trial.value))
  }

  def makeUserPremium(user: UserT, start: Long, expiry: Long): EitherT[F, UserNotFoundError, UserT] = {
    update(user.copy(userType = Premium.value, lastPaymentAt = start, nextPaymentAt = expiry))
  }

  def makeUserPremiumExpired(user: UserT): EitherT[F, UserNotFoundError, UserT] = {
    for {
      operationResult <- update(user.copy(userType = PremiumExpired.value, nextPaymentAt = -1))
      _ <- EitherT.liftF(alertsRepository.disableAllButFirstAlerts(user._id.toHexString))
    } yield operationResult
  }

  private def makeUserTrialExpired(user: UserT): EitherT[F, UserNotFoundError, UserT] = {
    for {
      updated <- update(user.copy(userType = UserType.TrialExpired.value, lastPaymentAt = -1, nextPaymentAt = -1))
      _ <- EitherT.liftF(alertsRepository.disableAllButFirstAlerts(updated._id.toHexString))
    } yield updated
  }


  private def update(user: UserT): EitherT[F, UserNotFoundError, UserT] = {
    for {
      updated <- userRepository.update(user).toRight(UserNotFoundError("User not found"))
    } yield updated

  }

}
