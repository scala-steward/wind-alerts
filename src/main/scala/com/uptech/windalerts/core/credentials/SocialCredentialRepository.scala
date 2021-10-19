package com.uptech.windalerts.core.credentials

trait SocialCredentialsRepository[F[_]] {
  def create(credentials: SocialCredentials): F[SocialCredentials]

  def find(email: String, deviceType: String): F[Option[SocialCredentials]]
}
