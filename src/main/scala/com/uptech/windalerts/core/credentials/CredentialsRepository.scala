package com.uptech.windalerts.core.credentials

import cats.data.OptionT


trait CredentialsRepository[F[_]] {
  def count(email: String, deviceType: String): F[Int]

  def create(credentials: Credentials): F[Credentials]

  def findByCreds(email: String, deviceType: String): OptionT[F, Credentials]

  def updatePassword(userId: String, password: String): F[Unit]
}