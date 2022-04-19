package com.uptech.windalerts.core.social.subscriptions

import cats.data.EitherT
import cats.effect.Sync
import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.core.types._
import com.uptech.windalerts.core.user.UserId

class SocialPlatformSubscriptionsProvider[F[_] : Sync](
                                                        purchaseTokenRepository: PurchaseTokenRepository[F],
                                                        socialSubscription: SocialSubscription[F]) {
  def find(userId: String): EitherT[F, SurfsUpError, SubscriptionPurchase] = {
    for {
      token <- purchaseTokenRepository.getLastForUser(userId)
      purchase <- getPurchase(token.purchaseToken)
    } yield purchase
  }

  def handleNewPurchase(user: UserId, req: PurchaseReceiptValidationRequest) = {
    for {
      _ <- getPurchase(req.token)
      savedToken <- purchaseTokenRepository.create(user.id, req.token, System.currentTimeMillis())
    } yield savedToken
  }

  def getPurchase(receiptData: String): EitherT[F, SurfsUpError, SubscriptionPurchase] =
    EitherT.right(socialSubscription.getPurchase(receiptData))

  def getLatestForToken(purchaseToken: String): EitherT[F, SurfsUpError, SubscriptionPurchaseWithUser] = {
    for {
      purchaseToken <- purchaseTokenRepository.getPurchaseByToken(purchaseToken)
      subscriptionPurchase <- getPurchase(purchaseToken.purchaseToken)
    } yield SubscriptionPurchaseWithUser(UserId(purchaseToken.userId), subscriptionPurchase)
  }
}