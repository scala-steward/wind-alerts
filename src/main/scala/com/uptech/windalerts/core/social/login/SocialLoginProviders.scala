package com.uptech.windalerts.core.social.login

trait SocialLoginProviders[F[_]] {
  def fetchUserFromPlatform(sccessRequest: AccessRequest): F[SocialUser]
}
