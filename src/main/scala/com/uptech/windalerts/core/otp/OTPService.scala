package com.uptech.windalerts.core.otp

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import com.uptech.windalerts.Repos
import com.uptech.windalerts.core.user.AuthenticationService
import com.uptech.windalerts.domain.SurfsUpError

import scala.util.Random

class OTPService[F[_]](repos: Repos[F]) {
  def send(userId: String, email: String)(implicit M: Monad[F]):EitherT[F, SurfsUpError, Unit] = {

    EitherT.liftF(for {
      otp <- createOtp(4)
      _ <- repos.otp().updateForUser(userId, OTPWithExpiry(otp, System.currentTimeMillis() + 5 * 60 * 1000, userId))
      result <- repos.emailConf().sendOtp(email, otp)
    } yield result)
  }

  def createOtp(n: Int)(implicit F: Monad[F]) = {
    val alpha = "0123456789"
    val size = alpha.size

    F.pure((1 to n).map(_ => alpha(Random.nextInt.abs % size)).mkString)
  }
}
