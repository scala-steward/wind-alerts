package com.uptech.windalerts.core.user

trait PasswordNotifier[F[_]] {
  def notifyNewPassword(firstName: String, to: String, password: String): F[String]
}
