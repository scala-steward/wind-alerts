package com.uptech.windalerts.core.social.login

trait SocialLoginProvider[F[_], T <: AccessRequest] {
  def fetchUserFromPlatform(registerRequest: T): F[SocialUser]
}