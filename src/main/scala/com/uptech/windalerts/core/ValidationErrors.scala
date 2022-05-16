package com.uptech.windalerts.core

abstract class SurfsUpError (val message: String) extends  Throwable

case class TokenExpiredError(msg: String = "Token not found") extends SurfsUpError(msg)

case class UserNotFoundError(msg: String = "User not found") extends SurfsUpError(msg)

case class BeachNotFoundError(msg: String = "Beach not found") extends SurfsUpError(msg)

case class OtpNotFoundError(msg: String = "OTP not found") extends SurfsUpError(msg)

case class TokenNotFoundError(msg: String = "Token not found") extends SurfsUpError(msg)

case class AlertNotFoundError(msg: String = "Alert not found") extends SurfsUpError(msg)

case class UserAlreadyExistsRegistered(email: String, deviceType: String) extends SurfsUpError("")

case class UserAuthenticationFailedError(email: String) extends SurfsUpError("")

case class RefreshTokenNotFoundError(msg: String = "Refresh token not found") extends SurfsUpError(msg)

case class RefreshTokenExpiredError(msg: String = "Refresh token expired") extends SurfsUpError(msg)

case class OperationNotAllowed(msg: String) extends SurfsUpError(msg)

case class UnknownError(msg: String) extends SurfsUpError(msg)

case class WWError(msg: String = "Error from WillyWeather") extends SurfsUpError(msg)

case class PlatformNotSupported(msg: String = "Platform not supported") extends SurfsUpError(msg)

case class NotificationNotSentError(msg: String = "Error while sending notification") extends SurfsUpError(msg)
