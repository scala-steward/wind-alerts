package com.uptech.windalerts.core.user.credentials

import cats.data.OptionT


trait CredentialsRepository[F[_]] {
  def create(email: String, password: String, deviceType: String): F[Credentials]

  def findByEmailAndDeviceType(email: String, deviceType: String): OptionT[F, Credentials]

  def updatePassword(userId: String, password: String): F[Unit]
}