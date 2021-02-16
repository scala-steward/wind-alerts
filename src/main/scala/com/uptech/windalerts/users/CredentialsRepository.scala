package com.uptech.windalerts.users

import cats.data.OptionT
import com.uptech.windalerts.domain.domain.Credentials


trait CredentialsRepository[F[_]] {
  def count(email: String, deviceType: String): F[Int]

  def create(credentials: Credentials): F[Credentials]

  def findByCreds(email: String, deviceType: String): OptionT[F, Credentials]

  def updatePassword(userId: String, password: String): OptionT[F, Unit]
}