package com.uptech.windalerts.core.otp

import cats.data.OptionT

trait OtpRepository[F[_]] {
  def findByOtpAndUserId(otp: String, userId: String): OptionT[F, OTPWithExpiry]

  def updateForUser(userId: String, otp: String, expiry: Long): F[OTPWithExpiry]

  def deleteForUser(userId: String): F[Unit]
}