package com.uptech.windalerts.core.credentials

trait SocialCredentialsRepository[F[_]] {
  def create(email: String, socialId: String, deviceType: String): F[SocialCredentials]

  def find(email: String, deviceType: String): F[Option[SocialCredentials]]
}
