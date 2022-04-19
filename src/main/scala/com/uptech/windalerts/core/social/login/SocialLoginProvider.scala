package com.uptech.windalerts.core.social.login

trait SocialLoginProvider[F[_]] {
  def fetchUserFromPlatform(accessToken: String,
                            deviceType: String,
                            deviceToken: String,
                            name: Option[String]): F[SocialUser]
}