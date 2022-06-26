package com.uptech.windalerts.core.user

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import com.uptech.windalerts.core.alerts.AlertsRepository
import com.uptech.windalerts.core.otp.OtpRepository
import com.uptech.windalerts.core.social.subscriptions.SubscriptionService
import com.uptech.windalerts.core.types._
import com.uptech.windalerts.core.{OtpNotFoundError, PlatformNotSupported, TokenNotFoundError, UserNotFoundError}
import com.uptech.windalerts.infrastructure.social.SocialPlatformTypes

class UserRolesService[F[_] : Sync](alertsRepository: AlertsRepository[F], userRepository: UserRepository[F], otpRepository: OtpRepository[F], subscriptionService: SubscriptionService[F]) {
  def updateTrialUsers()(implicit FR: Raise[F, UserNotFoundError]) = {
    for {
      users <- userRepository.findTrialExpiredUsers()
      _ <- makeUsersTrialExpired(users)
    } yield ()
  }

  private def makeUsersTrialExpired(users: Seq[UserT])(implicit FR: Raise[F, UserNotFoundError]): F[List[UserT]] = {
    users.map(user => makeUserTrialExpired(user)).toList.sequence
  }

  def updateSubscribedUsers()(implicit FR: Raise[F, TokenNotFoundError], UNF: Raise[F, UserNotFoundError], PNS: Raise[F, PlatformNotSupported]) = {
    for {
      users <- userRepository.findPremiumExpiredUsers()
      _ <- users.map(user => updateSubscribedUser(user)).toList.sequence
    } yield ()
  }

  private def updateSubscribedUser(user: UserT)(implicit M: Monad[F], FR: Raise[F, TokenNotFoundError], UNF: Raise[F, UserNotFoundError], PNS: Raise[F, PlatformNotSupported]): F[Unit] = {

    for {
      socialPlatformType <- SocialPlatformTypes[F](user.deviceType)
      purchase <- subscriptionService.findForUser(user.id, socialPlatformType)
      _ <- updateSubscribedUserRole(user, purchase.startTimeMillis, purchase.expiryTimeMillis)
    } yield ()
  }


  def updateSubscribedUserRole(user: UserT, startTime: Long, expiryTime: Long)(implicit FR: Raise[F, UserNotFoundError]) = {
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


  def handleUpdate(socialPlatformType: String, purchaseToken: String)(implicit FR: Raise[F, UserNotFoundError], TNF: Raise[F, TokenNotFoundError], PNS: Raise[F, PlatformNotSupported]) = {
    for {
      socialPlatformType_ <- SocialPlatformTypes[F](socialPlatformType)
      purchase <- subscriptionService.getLatestForToken(purchaseToken, socialPlatformType_)
      user <- userRepository.getByUserId(purchase.userId.id)
      updatedUser <- updateSubscribedUserRole(user, purchase.subscriptionPurchase.startTimeMillis, purchase.subscriptionPurchase.expiryTimeMillis)
    } yield updatedUser
  }

  def verifyEmail(user: UserId, request: OTP)(implicit FR: Raise[F, OtpNotFoundError], UNF: Raise[F, UserNotFoundError]): F[UserT] = {
    for {
      _ <- otpRepository.findByOtpAndUserId(request.otp, user.id)
      user <- userRepository.getByUserId(user.id)
      updateResult <- makeUserTrial(user)
      _ <- otpRepository.deleteForUser(user.id)
    } yield updateResult
  }

  def updateUserPurchase(u: UserId)(implicit M: Monad[F], FR: Raise[F, UserNotFoundError], TNF: Raise[F, TokenNotFoundError], PNS: Raise[F, PlatformNotSupported]) = {
    for {
      dbUser <- userRepository.getByUserId(u.id)
      socialPlatformType_ <- SocialPlatformTypes[F](dbUser.deviceType)
      purchase <- subscriptionService.findForUser(u.id, socialPlatformType_)
      userWithUpdatedRole <- updateSubscribedUserRole(dbUser, purchase.startTimeMillis, purchase.expiryTimeMillis)
    } yield userWithUpdatedRole
  }

  def makeUserTrial(user: UserT)(implicit FR: Raise[F, UserNotFoundError]): F[UserT] = {
    update(user.makeTrial())
  }

  def makeUserPremium(user: UserT, start: Long, expiry: Long)(implicit FR: Raise[F, UserNotFoundError]): F[UserT] = {
    update(user.makePremium(start, expiry))
  }

  def makeUserPremiumExpired(user: UserT)(implicit FR: Raise[F, UserNotFoundError]): F[UserT] = {
    for {
      operationResult <- update(user.makePremiumExpired())
      _ <- alertsRepository.disableAllButFirstAlerts(user.id)
    } yield operationResult
  }

  private def makeUserTrialExpired(user: UserT)(implicit FR: Raise[F, UserNotFoundError]): F[UserT] = {
    for {
      updated <- update(user.makeTrialExpired())
      _ <- alertsRepository.disableAllButFirstAlerts(updated.id)
    } yield updated
  }

  private def update(user: UserT)(implicit FR: Raise[F, UserNotFoundError]): F[UserT] = {
    userRepository.update(user)
  }

}
