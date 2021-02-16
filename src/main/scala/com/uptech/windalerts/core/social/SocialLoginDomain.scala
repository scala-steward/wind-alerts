package com.uptech.windalerts.core.social

import com.uptech.windalerts.domain.domain.{SocialUser, SurfsUpEitherT}

object SocialLoginDomain {
  sealed trait AccessRequest

  case class FacebookAccessRequest(
        accessToken: String,
        deviceType: String,
        deviceToken: String) extends AccessRequest

  case class AppleAccessRequest(
       authorizationCode: String,
       nonce: String,
       deviceType: String,
       deviceToken: String,
       name: String) extends AccessRequest

  trait SocialPlatform[F[_], T<:AccessRequest] {
    def fetchUserFromPlatform(registerRequest: T):SurfsUpEitherT[F, SocialUser]
  }
}
