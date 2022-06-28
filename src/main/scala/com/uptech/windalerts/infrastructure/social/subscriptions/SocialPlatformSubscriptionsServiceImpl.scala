package com.uptech.windalerts.infrastructure.social.subscriptions

import cats.{Applicative, Monad}
import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import com.uptech.windalerts.core.social.SocialPlatformType
import com.uptech.windalerts.core.social.subscriptions._
import com.uptech.windalerts.core.types._
import com.uptech.windalerts.core.user.UserId
import com.uptech.windalerts.core.{PlatformNotSupported, TokenNotFoundError}
import com.uptech.windalerts.infrastructure.social.SocialPlatformTypes
import com.uptech.windalerts.infrastructure.social.SocialPlatformTypes.{Apple, Google}

class SocialPlatformSubscriptionsServiceImpl[F[_] : Sync](
                                                           applePurchaseRepository: PurchaseTokenRepository[F],
                                                           androidPurchaseRepository: PurchaseTokenRepository[F],
                                                           appleSubscription: SocialSubscriptionProvider[F],
                                                           androidSubscription: SocialSubscriptionProvider[F]) {
  def find(userId: String, deviceType: String)(implicit M: Monad[F],  FR: Raise[F, TokenNotFoundError], PNS: Raise[F, PlatformNotSupported]): F[SubscriptionPurchase] = {
    for {
      platformType <- SocialPlatformTypes[F](deviceType)
      purchase <- find(userId, platformType)
    } yield purchase
  }

  def find(userId: String, platformType: SocialPlatformType)(implicit FR: Raise[F, TokenNotFoundError]): F[SubscriptionPurchase] = {
    for {
      token <- findRepo(platformType).getLastForUser(userId)
      purchase <- getPurchase(platformType, token.purchaseToken)
    } yield purchase
  }

  def handleNewPurchase(platformType: SocialPlatformType, user: UserId, request: PurchaseReceiptValidationRequest)(implicit FR: Raise[F, TokenNotFoundError])
  : F[PurchaseToken] = {
    platformType match {
      case Apple => handleNewPurchase(user, request, applePurchaseRepository, appleSubscription)
      case Google => handleNewPurchase(user, request, androidPurchaseRepository, androidSubscription)
    }
  }

  private def handleNewPurchase(user: UserId, req: PurchaseReceiptValidationRequest, repository: PurchaseTokenRepository[F], subscription: SocialSubscriptionProvider[F])(implicit FR: Raise[F, TokenNotFoundError]) = {
    for {
      _ <- getPurchase(subscription, req.token)
      savedToken <- repository.create(user.id, req.token, System.currentTimeMillis())
    } yield savedToken
  }

  def getPurchase(platformType: SocialPlatformType, token: String): F[SubscriptionPurchase] = {
    platformType match {
      case Apple => getPurchase(appleSubscription, token)
      case Google => getPurchase(androidSubscription, token)
    }
  }

  def getPurchase(subscription: SocialSubscriptionProvider[F], receiptData: String): F[SubscriptionPurchase] =
    subscription.getPurchaseFromPlatform(receiptData)

  private def findRepo(platformType: SocialPlatformType) = {
    platformType match {
      case Apple => applePurchaseRepository
      case Google => androidPurchaseRepository
    }
  }

  def getLatestForToken(platformType: SocialPlatformType, purchaseToken: String)(implicit FR: Raise[F, TokenNotFoundError]): F[SubscriptionPurchaseWithUser] = {
    getLatestForToken(platformType, findRepo(platformType), purchaseToken)
  }

  def getLatestForToken(platformType: SocialPlatformType, repository: PurchaseTokenRepository[F], purchaseToken: String)(implicit FR: Raise[F, TokenNotFoundError]):F[SubscriptionPurchaseWithUser] = {
    for {
      purchaseToken <- repository.getByToken(purchaseToken)
      subscriptionPurchase <- getPurchase(platformType, purchaseToken.purchaseToken)
    } yield SubscriptionPurchaseWithUser(UserId(purchaseToken.userId), subscriptionPurchase)
  }
}
