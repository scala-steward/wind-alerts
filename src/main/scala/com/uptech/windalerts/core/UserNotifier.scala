package com.uptech.windalerts.core

import cats.Monad

trait UserNotifier[F[_]] {
  def notifyOTP(to: String, otp: String)(implicit F: Monad[F]): F[String]

  def notifyNewPassword(firstName: String, to: String, password: String)(implicit F: Monad[F]): F[String]
}
