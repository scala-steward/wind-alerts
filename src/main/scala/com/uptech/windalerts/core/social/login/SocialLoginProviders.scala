package com.uptech.windalerts.core.social.login

import com.uptech.windalerts.core.social.SocialPlatformType

trait SocialLoginProviders[F[_]] {
  def findByType(socialPlatformType: SocialPlatformType): SocialLoginProvider[F]
}