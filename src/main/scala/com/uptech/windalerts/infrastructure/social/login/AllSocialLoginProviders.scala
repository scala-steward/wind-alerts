package com.uptech.windalerts.infrastructure.social.login

import com.uptech.windalerts.core.social.SocialPlatformType
import com.uptech.windalerts.core.social.login._
import com.uptech.windalerts.infrastructure.social.SocialPlatformTypes.{Apple, Facebook, Google}

class AllSocialLoginProviders[F[_]](
                                     appleLoginProvider: AppleLoginProvider[F],
                                     facebookLoginProvider: FacebookLoginProvider[F]) extends SocialLoginProviders[F] {
  override def findByType(platformType: SocialPlatformType) = {
    platformType match {
      case Apple => appleLoginProvider
      case Facebook => facebookLoginProvider
    }
  }
}