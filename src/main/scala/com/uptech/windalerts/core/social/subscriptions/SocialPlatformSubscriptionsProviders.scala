package com.uptech.windalerts.core.social.subscriptions

import com.uptech.windalerts.core.social.SocialPlatformType

trait SocialPlatformSubscriptionsProviders[F[_]] {
  def findByType(deviceType: String): SocialPlatformSubscriptionsProvider[F]

  def findByType(platformType: SocialPlatformType): SocialPlatformSubscriptionsProvider[F]
}

