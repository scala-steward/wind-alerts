package com.uptech.windalerts.users

trait ValidationError extends RuntimeException with Serializable
case class TokenExpiredError() extends ValidationError
case class UserNotFoundError() extends ValidationError
case class OtpNotFoundError() extends ValidationError
case class TokenNotFoundError() extends ValidationError
case class AlertNotFoundError() extends ValidationError

case class UserAlreadyExistsError(email: String, deviceType:String) extends ValidationError
case class UserAuthenticationFailedError(email: String) extends ValidationError
case class RefreshTokenNotFoundError() extends ValidationError
case class RefreshTokenExpiredError() extends ValidationError
case class CouldNotUpdateUserDeviceError() extends ValidationError
case class CouldNotUpdatePasswordError() extends ValidationError
case class CouldNotUpdateUserError() extends ValidationError
case class OperationNotAllowed(message:String) extends ValidationError
case class UnknownError(message:String) extends ValidationError
case class RequestFailedError(message:String) extends ValidationError
case class ResponseParsingFailed(exception: Exception) extends ValidationError
