package com.uptech.windalerts.domain

class SurfsUpError(message:String) extends RuntimeException(message) with Serializable
case class TokenExpiredError(message: String = "Token not found") extends SurfsUpError(message)
case class UserNotFoundError(message: String = "User not found") extends SurfsUpError(message)
case class BeachNotFoundError(message: String = "Beach not found") extends SurfsUpError(message)

case class OtpNotFoundError(message: String = "OTP not found") extends SurfsUpError(message)
case class TokenNotFoundError(message: String = "Token not found") extends SurfsUpError(message)
case class AlertNotFoundError(message: String = "Alert not found") extends SurfsUpError(message)

case class UserAlreadyExistsError(email: String, deviceType:String) extends SurfsUpError("")
case class UserAuthenticationFailedError(email: String) extends SurfsUpError("")
case class RefreshTokenNotFoundError(message: String = "Refresh token not found") extends SurfsUpError(message)
case class RefreshTokenExpiredError(message: String = "Refresh token expired") extends SurfsUpError(message)
case class OperationNotAllowed(message:String) extends SurfsUpError(message)
case class UnknownError(message:String) extends SurfsUpError(message)
case class WWError(message: String = "Error from WillyWeather") extends SurfsUpError(message)
