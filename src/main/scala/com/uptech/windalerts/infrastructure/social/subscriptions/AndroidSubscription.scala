package com.uptech.windalerts.infrastructure.social.subscriptions

import cats.effect.Sync
import com.google.api.services.androidpublisher.AndroidPublisher
import com.uptech.windalerts.core.social.subscriptions.{SocialSubscription, SubscriptionPurchase}
import io.scalaland.chimney.dsl._

class AndroidSubscription[F[_] : Sync](androidPublisher:AndroidPublisher)(implicit F: Sync[F]) extends SocialSubscription[F] {
  override def getPurchase(token: String): F[SubscriptionPurchase] = {
    F.pure({
      androidPublisher.purchases().subscriptions().get(ApplicationConfig.PACKAGE_NAME, "surfsup_01month_subs", token).execute().into[SubscriptionPurchase].enableBeanGetters
        .withFieldComputed(_.expiryTimeMillis, _.getExpiryTimeMillis.toLong)
        .withFieldComputed(_.startTimeMillis, _.getStartTimeMillis.toLong).transform
    })
  }
}
