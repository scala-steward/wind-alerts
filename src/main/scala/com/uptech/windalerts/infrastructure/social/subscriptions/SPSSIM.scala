package com.uptech.windalerts.infrastructure.social.subscriptions

import cats.effect.Sync
import com.uptech.windalerts.core.social.SocialPlatformType
import com.uptech.windalerts.core.social.subscriptions.{PurchaseTokenRepository, SocialPlatformSubscriptionsProvider, SocialPlatformSubscriptionsProviders, SocialSubscription}
import com.uptech.windalerts.infrastructure.social.SocialPlatformTypes
import com.uptech.windalerts.infrastructure.social.SocialPlatformTypes.{Apple, Google}

class AllSocialPlatformSubscriptionsProviders[F[_] : Sync](applePurchaseRepository: PurchaseTokenRepository[F],
                                                           androidPurchaseRepository: PurchaseTokenRepository[F],
                                                           appleSubscription: SocialSubscription[F],
                                                           androidSubscription: SocialSubscription[F]) extends SocialPlatformSubscriptionsProviders[F] {

  override def findByType(platformType: String) = {
    SocialPlatformTypes(platformType) match {
      case Some(Apple) => new SocialPlatformSubscriptionsProvider(applePurchaseRepository, appleSubscription)
      case Some(Google) => new SocialPlatformSubscriptionsProvider(androidPurchaseRepository, androidSubscription)
    }
  }

  override def findByType(platformType: SocialPlatformType) = {
    platformType match {
      case Apple => new SocialPlatformSubscriptionsProvider(applePurchaseRepository, appleSubscription)
      case Google => new SocialPlatformSubscriptionsProvider(androidPurchaseRepository, androidSubscription)
    }
  }
}
