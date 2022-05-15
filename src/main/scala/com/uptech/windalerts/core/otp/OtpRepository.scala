package com.uptech.windalerts.core.otp

import cats.mtl.Raise
import com.uptech.windalerts.core.OtpNotFoundError

trait OtpRepository[F[_]] {
  def findByOtpAndUserId(otp: String, userId: String)(implicit FR: Raise[F, OtpNotFoundError]): F[OTPWithExpiry]

  def updateForUser(userId: String, otp: String, expiry: Long): F[OTPWithExpiry]

  def deleteForUser(userId: String): F[Unit]
}