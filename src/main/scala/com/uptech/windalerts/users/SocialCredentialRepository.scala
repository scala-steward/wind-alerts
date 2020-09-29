package com.uptech.windalerts.users

trait SocialCredentialsRepository[F[_], T] {
  def create(credentials: T): F[T]

  def count(email: String, deviceType: String): F[Int]

  def find(email: String, deviceType: String): F[Option[T]]
}
