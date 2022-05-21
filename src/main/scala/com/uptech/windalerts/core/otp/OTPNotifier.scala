package com.uptech.windalerts.core.otp

import cats.Monad

trait OTPNotifier[F[_]] {
  def notifyOTP(to: String, otp: String)(implicit F: Monad[F]): F[String]
}
