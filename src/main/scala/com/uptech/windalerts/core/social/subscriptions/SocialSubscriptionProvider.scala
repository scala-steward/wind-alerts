package com.uptech.windalerts.core.social.subscriptions

trait SocialSubscriptionProvider[F[_]] {
  def getPurchaseFromPlatform(token: String): F[SubscriptionPurchase]
}
