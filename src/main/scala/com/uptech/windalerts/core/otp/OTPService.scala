package com.uptech.windalerts.core.otp

import cats.Monad
import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.core.user.UserRepository
import com.uptech.windalerts.core.{SurfsUpError, UnknownError, UserNotFoundError}
import com.uptech.windalerts.core.EmailSender
import com.uptech.windalerts.infrastructure.endpoints.codecs._
import com.uptech.windalerts.infrastructure.endpoints.dtos._
import io.circe.parser.parse

import scala.util.Random

class OTPService[F[_] : Sync](otpRepository: OtpRepository[F], emailSender: EmailSender[F]) {

  def send(userId: String, email: String)(implicit M: Monad[F]):EitherT[F, UnknownError, String] = {
    val otp = createOtp(4)
    for {
      _ <- EitherT.liftF(otpRepository.updateForUser(userId, otp, System.currentTimeMillis() + 5 * 60 * 1000))
      result <- emailSender.sendOtp(email, otp).leftMap(UnknownError(_))
    } yield result
  }

  def createOtp(n: Int) = {
    val alpha = "0123456789"
    val size = alpha.size

    (1 to n).map(_ => alpha(Random.nextInt.abs % size)).mkString
  }

}
