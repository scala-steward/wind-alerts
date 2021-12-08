package com.uptech.windalerts.infrastructure.social.login

import com.uptech.windalerts.core.social.login._
import com.uptech.windalerts.infrastructure.social.login.AccessRequests.{AppleAccessRequest, FacebookAccessRequest}

class FixedSocialLoginProviders[F[_]](appleLoginProvider: AppleLoginProvider[F], facebookLoginProvider: FacebookLoginProvider[F]) extends SocialLoginProviders[F] {
  override def fetchUserFromPlatform(accessRequest: AccessRequest): F[SocialUser] = {
    accessRequest match {
      case a: AppleAccessRequest => appleLoginProvider.fetchUserFromPlatform(a)
      case f: FacebookAccessRequest => facebookLoginProvider.fetchUserFromPlatform(f)
    }
  }
}
