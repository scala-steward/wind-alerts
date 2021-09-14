package com.uptech.windalerts.core.otp

import cats.Monad
import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.core.UserNotFoundError
import com.uptech.windalerts.core.user.UserRepository
import com.uptech.windalerts.infrastructure.EmailSender

import scala.util.Random

class OTPService[F[_] : Sync](otpRepository: OtpRepository[F], emailSender: EmailSender[F], userRepository: UserRepository[F]) {

  def sendOtp(userId: String): EitherT[F, UserNotFoundError, Unit] = {
    for {
      userFromDb <- userRepository.getByUserId(userId).toRight(UserNotFoundError())
      sent <- EitherT.right(send(userFromDb._id.toHexString, userFromDb.email))
    } yield sent
  }

  def send(userId: String, email: String)(implicit M: Monad[F]):F[Unit] = {
    for {
      otp <- M.pure(createOtp(4))
      _ <- otpRepository.updateForUser(userId, OTPWithExpiry(otp, System.currentTimeMillis() + 5 * 60 * 1000, userId))
      result <- emailSender.sendOtp(email, otp)
    } yield result
  }

  def createOtp(n: Int) = {
    val alpha = "0123456789"
    val size = alpha.size

    (1 to n).map(_ => alpha(Random.nextInt.abs % size)).mkString
  }

}
