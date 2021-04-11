package com.uptech.windalerts.infrastructure.social.subscriptions

import cats.effect.Sync
import com.uptech.windalerts.Repos
import com.uptech.windalerts.core.social.subscriptions.{SocialSubscription, SubscriptionPurchase}
import io.scalaland.chimney.dsl._

class AndroidSubscription[F[_] : Sync](repos: Repos[F])(implicit F: Sync[F]) extends SocialSubscription[F] {
  override def getPurchase(token: String, productId: String): F[SubscriptionPurchase] = {
    F.pure({
      repos.androidPublisher().purchases().subscriptions().get(ApplicationConfig.PACKAGE_NAME, productId, token).execute().into[SubscriptionPurchase].enableBeanGetters
        .withFieldComputed(_.expiryTimeMillis, _.getExpiryTimeMillis.toLong)
        .withFieldComputed(_.startTimeMillis, _.getStartTimeMillis.toLong).transform
    })
  }
}
