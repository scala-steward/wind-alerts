package com.uptech.windalerts.users

import cats.data.{EitherT, OptionT}
import cats.effect.IO
import com.uptech.windalerts.domain.domain.{Credentials, OTPWithExpiry}

trait OtpRepository {
  def exists(otp: String, userId:String): EitherT[IO, OtpNotFoundError, OTPWithExpiry]
  def create(otp: OTPWithExpiry): IO[OTPWithExpiry]
}
