package com.uptech.windalerts.core

import cats.Monad
import cats.data.EitherT

trait EmailSender[F[_]] {
  def sendOtp(to: String, otp: String)(implicit F: Monad[F]): F[String]

  def sendResetPassword(firstName: String, to: String, password: String)(implicit F: Monad[F]): F[String]
}
