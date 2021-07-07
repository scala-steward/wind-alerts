package com.uptech.windalerts.core.user

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.config.secrets
import com.uptech.windalerts.core.alerts.Alerts
import com.uptech.windalerts.core.otp.OtpRepository
import com.uptech.windalerts.core.social.subscriptions.{AndroidTokenRepository, AppleTokenRepository, SubscriptionsService}
import com.uptech.windalerts.core.user.UserType.{Premium, PremiumExpired, Trial}
import com.uptech.windalerts.core.{OperationNotAllowed, SurfsUpError, UnknownError, UserNotFoundError}
import com.uptech.windalerts.infrastructure.endpoints.codecs._
import com.uptech.windalerts.infrastructure.endpoints.dtos._
import com.uptech.windalerts.infrastructure.repositories.mongo.Repos
import io.circe.parser.parse

class UserRolesService[F[_] : Sync](applePurchaseRepository: AppleTokenRepository[F],
                                    androidPurchaseRepository: AndroidTokenRepository[F],
                                    userRepository: UserRepository[F],
                                    otpRepository: OtpRepository[F],
                                    repos: Repos[F],
                                    subscriptionsService: SubscriptionsService[F], userService: UserService[F]) {
  def makeUserTrial(user: UserT): EitherT[F, UserNotFoundError, UserT] = {
    userRepository.update(user.copy(
      userType = Trial.value,
      startTrialAt = System.currentTimeMillis(),
      endTrialAt = System.currentTimeMillis() + (30L * 24L * 60L * 60L * 1000L),
    )).toRight(UserNotFoundError("User not found"))
  }

  def makeUserPremium(user: UserT, start: Long, expiry: Long): EitherT[F, UserNotFoundError, UserT] = {
    userRepository.update(user.copy(userType = Premium.value, lastPaymentAt = start, nextPaymentAt = expiry)).toRight(UserNotFoundError())
  }

  def makeUserPremiumExpired(user: UserT): EitherT[F, UserNotFoundError, UserT] = {
    for {
      operationResult <- userRepository.update(user.copy(userType = PremiumExpired.value, nextPaymentAt = -1)).toRight(UserNotFoundError())
      _ <- EitherT.liftF(repos.alertsRepository().disableAllButOneAlerts(user._id.toHexString))
    } yield operationResult
  }

  private def makeUserTrialExpired(eitherUser: UserT): EitherT[F, UserNotFoundError, UserT] = {
    for {
      updated <- update(eitherUser.copy(userType = UserType.TrialExpired.value, lastPaymentAt = -1, nextPaymentAt = -1))
      _ <- EitherT.liftF(repos.alertsRepository().disableAllButOneAlerts(updated._id.toHexString))
    } yield updated
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

  def updateTrialUsers() = {
    for {
      users <- EitherT.right(userRepository.findTrialExpiredUsers())
      _ <- makeAllTrialExpired(users)
    } yield ()
  }

  private def makeAllTrialExpired(users: Seq[UserT]):EitherT[F, UserNotFoundError, List[UserT]] = {
    users.map(user => makeUserTrialExpired(user)).toList.sequence
  }

  def updateAndroidSubscribedUsers() = {
    for {
      users <- EitherT.right(userRepository.findAndroidPremiumExpiredUsers())
      _ <- users.map(user=>updateAndroidSubscribedUser(user)).toList.sequence
    } yield ()
  }

  private def updateAndroidSubscribedUser(user:UserT ):EitherT[F, SurfsUpError, Unit] = {
    for {
      token <- androidPurchaseRepository.getLastForUser(user._id.toHexString)
      purchase <- subscriptionsService.getAndroidPurchase(token.subscriptionId, token.purchaseToken)
      _ <- updateSubscribedUserRole(user, purchase.startTimeMillis, purchase.expiryTimeMillis).leftWiden[SurfsUpError]
    } yield ()
  }

  def updateAppleSubscribedUsers():EitherT[F, SurfsUpError, Unit] = {
    for {
      users <- EitherT.right(userRepository.findApplePremiumExpiredUsers())
      _ <- users.map(user=>updateAppleSubscribedUser(user)).toList.sequence
    } yield ()
  }

  private def updateAppleSubscribedUser(user:UserT) = {
    for {
      token <- applePurchaseRepository.getLastForUser(user._id.toHexString)
      purchase <- subscriptionsService.getApplePurchase(token.purchaseToken, secrets.read.surfsUp.apple.appSecret)
      _ <- updateSubscribedUserRole(user, purchase.startTimeMillis, purchase.expiryTimeMillis).leftWiden[SurfsUpError]
    } yield ()
  }

  def update(user: UserT): EitherT[F, UserNotFoundError, UserT] =
    userRepository.update(user).toRight(UserNotFoundError("User not found"))

  def authorizePremiumUsers(user: UserT): EitherT[F, SurfsUpError, UserT] = {
    EitherT.fromEither(if (UserType(user.userType) == UserType.Premium || UserType(user.userType) == UserType.Trial) {
      Right(user)
    } else {
      Left(OperationNotAllowed(s"Please subscribe to perform this action"))
    })
  }
  
  def authorizeAlertEditRequest(user: UserT, alertId: String, alertRequest: AlertRequest): EitherT[F, OperationNotAllowed, UserT] = {
    EitherT.liftF(repos.alertsRepository().getAllForUser(user._id.toHexString))
      .flatMap(alerts => EitherT.fromEither(authorizeAlertEditRequest(user, alertId, alerts, alertRequest)))
  }

  private def authorizeAlertEditRequest(user: UserT, alertId: String, alert: Alerts, alertRequest: AlertRequest): Either[OperationNotAllowed, UserT] = {
    if (UserType(user.userType) == UserType.Premium || UserType(user.userType) == UserType.Trial) {
      Right(user)
    } else {
      Either.cond(checkForNonPremiumUser(alertId, alert, alertRequest), user, OperationNotAllowed(s"Please subscribe to perform this action"))
    }
  }

  def checkForNonPremiumUser(alertId: String, alerts: Alerts, alertRequest: AlertRequest) = {
    val alertOption = alerts.alerts.sortBy(_.createdAt).headOption


    alertOption.map(alert => {
      if (alert._id.toHexString != alertId) {
        false
      } else {
        alert.allFieldExceptStatusAreSame(alertRequest)
      }
    }).getOrElse(false)
  }


  def handleAndroidUpdate(update: AndroidUpdate):EitherT[F, SurfsUpError, UserT] = {
    for {
      decoded <- EitherT.fromEither[F](Either.right(new String(java.util.Base64.getDecoder.decode(update.message.data))))
      subscription <- asSubscription(decoded)
      token <- androidPurchaseRepository.getPurchaseByToken(subscription.subscriptionNotification.purchaseToken)
      purchase <- subscriptionsService.getAndroidPurchase(token.subscriptionId, subscription.subscriptionNotification.purchaseToken)
      user <- userService.getUser(token.userId)
      updatedUser <- updateSubscribedUserRole(user, purchase.startTimeMillis, purchase.expiryTimeMillis).leftWiden[SurfsUpError]
    } yield updatedUser
  }


  def verifyEmail(user: UserId, request: OTP):EitherT[F, SurfsUpError, UserDTO] = {
    for {
      _ <- otpRepository.exists(request.otp, user.id)
      user <- userService.getUser(user.id)
      updateResult <- makeUserTrial(user).map(_.asDTO()).leftWiden[SurfsUpError]
    } yield updateResult
  }

  def getAndroidPurchase(u: UserId) = {
    for {
      token <- androidPurchaseRepository.getLastForUser(u.id)
      purchase <- subscriptionsService.getAndroidPurchase(token.subscriptionId, token.purchaseToken)
      dbUser <- userService.getUser(u.id).leftWiden[SurfsUpError]
      premiumUser <- updateSubscribedUserRole(dbUser, purchase.startTimeMillis, purchase.expiryTimeMillis).map(_.asDTO()).leftWiden[SurfsUpError]
    } yield premiumUser
  }

  private def asSubscription(response: String): EitherT[F, SurfsUpError, SubscriptionNotificationWrapper] = {
    EitherT.fromEither((for {
      parsed <- parse(response)
      decoded <- parsed.as[SubscriptionNotificationWrapper].leftWiden[io.circe.Error]
    } yield decoded).leftMap(error => UnknownError(error.getMessage)).leftWiden[SurfsUpError])

  }

  def updateAppleUser(user: UserId) = {
    for {
      token <- applePurchaseRepository.getLastForUser(user.id)
      purchase <- subscriptionsService.getApplePurchase(token.purchaseToken, secrets.read.surfsUp.apple.appSecret)
      dbUser <- userService.getUser(user.id).leftWiden[SurfsUpError]
      premiumUser <- updateSubscribedUserRole(dbUser, purchase.startTimeMillis, purchase.expiryTimeMillis).map(_.asDTO()).leftWiden[SurfsUpError]
    } yield premiumUser
  }
}
