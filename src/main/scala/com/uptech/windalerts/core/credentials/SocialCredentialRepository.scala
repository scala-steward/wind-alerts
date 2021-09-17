package com.uptech.windalerts.core.credentials

trait SocialCredentialsRepository[F[_], T] {
  def create(credentials: T): F[T]

  def find(email: String, deviceType: String): F[Option[T]]
}
