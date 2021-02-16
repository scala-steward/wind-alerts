package com.uptech.windalerts.core

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.Repos
import com.uptech.windalerts.core.social.SubscriptionsService
import com.uptech.windalerts.domain._
import com.uptech.windalerts.domain.domain.UserType.{Premium, PremiumExpired, Trial}
import com.uptech.windalerts.domain.domain._

class UserRolesService[F[_] : Sync](repos: Repos[F], subscriptionsService: SubscriptionsService[F]) {
  def makeUserTrial(user: UserT): EitherT[F, SurfsUpError, UserT] = {
    repos.usersRepo().update(user.copy(
      userType = Trial.value,
      startTrialAt = System.currentTimeMillis(),
      endTrialAt = System.currentTimeMillis() + (30L * 24L * 60L * 60L * 1000L),
    )).toRight(CouldNotUpdateUserError())
  }

  def makeUserPremium(user: UserT, start: Long, expiry: Long): EitherT[F, SurfsUpError, UserT] = {
    for {
      operationResult <- repos.usersRepo().update(user.copy(userType = Premium.value, lastPaymentAt = start, nextPaymentAt = expiry)).toRight(CouldNotUpdateUserError()).leftWiden[SurfsUpError]
    } yield operationResult
  }

  def makeUserPremiumExpired(user: UserT): EitherT[F, SurfsUpError, UserT] = {
    for {
      operationResult <- repos.usersRepo().update(user.copy(userType = PremiumExpired.value, nextPaymentAt = -1)).toRight(CouldNotUpdateUserError())
      _ <- EitherT.liftF(repos.alertsRepository().disableAllButOneAlerts(user._id.toHexString))
    } yield operationResult
  }

  private def makeUserTrialExpired(eitherUser: UserT): EitherT[F, SurfsUpError, UserT] = {
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
      users <- repos.usersRepo().findTrialExpiredUsers()
      _ <- convert(users.map(user => makeUserTrialExpired(user)).toList)
    } yield ()
  }

  def updateAndroidSubscribedUsers() = {
    for {
      users <- repos.usersRepo().findAndroidPremiumExpiredUsers()
      _ <- convert(users.map(user=>updateAndroidSubscribedUser(user)).toList)
    } yield ()
  }

  private def updateAndroidSubscribedUser(user:UserT ) = {
    for {
      token <- repos.androidPurchaseRepo().getLastForUser(user._id.toHexString)
      purchase <- subscriptionsService.getAndroidPurchase(token.subscriptionId, token.purchaseToken)
      _ <- updateSubscribedUserRole(user, purchase.startTimeMillis, purchase.expiryTimeMillis)
    } yield ()
  }

  def updateAppleSubscribedUsers() = {
    for {
      users <- repos.usersRepo().findApplePremiumExpiredUsers()
      _ <- convert(users.map(user=>updateAppleSubscribedUser(user)).toList)
    } yield ()
  }

  private def updateAppleSubscribedUser(user:UserT) = {
    for {
      token <- repos.applePurchaseRepo().getLastForUser(user._id.toHexString)
      purchase <- subscriptionsService.getApplePurchase(token.purchaseToken, secrets.read.surfsUp.apple.appSecret)
      _ <- updateSubscribedUserRole(user, purchase.purchase_date_ms, purchase.expires_date_ms)
    } yield ()
  }

  def update(user: UserT): EitherT[F, UserNotFoundError, UserT] =
    for {
      saved <- repos.usersRepo().update(user).toRight(UserNotFoundError())
    } yield saved

  def convert[A](list: List[EitherT[F, SurfsUpError, A]]) = {
    import cats.implicits._

    type Stack[A] = EitherT[F, SurfsUpError, A]

    val eitherOfList: Stack[List[A]] = list.sequence
    eitherOfList
  }

}
