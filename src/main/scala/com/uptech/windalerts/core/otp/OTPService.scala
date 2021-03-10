package com.uptech.windalerts.core.otp

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import com.uptech.windalerts.Repos
import com.uptech.windalerts.domain.SurfsUpError
import com.uptech.windalerts.users.AuthenticationService

class OTPService[F[_]](repos: Repos[F], auth: AuthenticationService[F]) {
  def send(userId: String, email: String)(implicit M: Monad[F]):EitherT[F, SurfsUpError, Unit] = {

    EitherT.liftF(for {
      otp <- auth.createOtp(4)
      _ <- repos.otp().updateForUser(userId, OTPWithExpiry(otp, System.currentTimeMillis() + 5 * 60 * 1000, userId))
      result <- repos.emailConf().sendOtp(email, otp)
    } yield result)
  }
}
