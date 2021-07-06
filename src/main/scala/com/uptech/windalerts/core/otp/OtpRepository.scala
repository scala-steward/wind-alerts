package com.uptech.windalerts.core.otp

import cats.Monad
import cats.data.EitherT
import com.uptech.windalerts.core.OtpNotFoundError

trait OtpRepository[F[_]] {
  def exists(otp: String, userId: String)(implicit M: Monad[F]): EitherT[F, OtpNotFoundError, OTPWithExpiry]

  def updateForUser(userId:String, otp: OTPWithExpiry)(implicit M: Monad[F]): F[OTPWithExpiry]
}