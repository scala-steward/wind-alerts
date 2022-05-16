package com.uptech.windalerts.core.social.subscriptions

import cats.data.EitherT
import cats.effect.Sync
import cats.mtl.Raise
import cats.implicits._
import com.uptech.windalerts.core.{SurfsUpError, TokenNotFoundError}
import com.uptech.windalerts.core.types._
import com.uptech.windalerts.core.user.UserId

class SocialPlatformSubscriptionsProvider[F[_] : Sync](
                                                        purchaseTokenRepository: PurchaseTokenRepository[F],
                                                        socialSubscription: SocialSubscription[F]) {
  def find(userId: String)(implicit FR: Raise[F, TokenNotFoundError]): F[SubscriptionPurchase] = {
    for {
      token <- purchaseTokenRepository.getLastForUser(userId)
      purchase <- getPurchase(token.purchaseToken)
    } yield purchase
  }

  def handleNewPurchase(user: UserId, req: PurchaseReceiptValidationRequest)(implicit FR: Raise[F, TokenNotFoundError]) = {
    for {
      _ <- getPurchase(req.token)
      savedToken <- purchaseTokenRepository.create(user.id, req.token, System.currentTimeMillis())
    } yield savedToken
  }

  def getPurchase(receiptData: String)=
    socialSubscription.getPurchase(receiptData)

  def getLatestForToken(purchaseToken: String)(implicit FR: Raise[F, TokenNotFoundError]): F[ SubscriptionPurchaseWithUser] = {
    for {
      purchaseToken <- purchaseTokenRepository.getPurchaseByToken(purchaseToken)
      subscriptionPurchase <- getPurchase(purchaseToken.purchaseToken)
    } yield SubscriptionPurchaseWithUser(UserId(purchaseToken.userId), subscriptionPurchase)
  }
}