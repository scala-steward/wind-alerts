package com.uptech.windalerts.users

import cats.data.EitherT
import com.uptech.windalerts.domain.OtpNotFoundError
import com.uptech.windalerts.domain.domain.OTPWithExpiry

trait OtpRepository[F[_]] {
  def exists(otp: String, userId: String): EitherT[F, OtpNotFoundError, OTPWithExpiry]


  def updateForUser(userId:String, otp: OTPWithExpiry): F[OTPWithExpiry]
}