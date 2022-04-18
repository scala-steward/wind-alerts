package com.uptech.windalerts.infrastructure.social.login

import com.uptech.windalerts.core.social.login._
import com.uptech.windalerts.infrastructure.social.login.AccessRequests.{AppleRegisterRequest, FacebookRegisterRequest}

class FixedSocialLoginProviders[F[_]](appleLoginProvider: AppleLoginProvider[F], facebookLoginProvider: FacebookLoginProvider[F]) extends SocialLoginProviders[F] {
  override def fetchUserFromPlatform(accessRequest: AccessRequest): F[SocialUser] = {
    accessRequest match {
      case a: AppleRegisterRequest => appleLoginProvider.fetchUserFromPlatform(a)
      case f: FacebookRegisterRequest => facebookLoginProvider.fetchUserFromPlatform(f)
    }
  }
}
