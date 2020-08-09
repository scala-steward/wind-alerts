package com.uptech.windalerts.domain

trait SurfsUpError extends RuntimeException with Serializable
case class TokenExpiredError() extends SurfsUpError
case class UserNotFoundError() extends SurfsUpError
case class OtpNotFoundError() extends SurfsUpError
case class TokenNotFoundError() extends SurfsUpError
case class AlertNotFoundError() extends SurfsUpError

case class UserAlreadyExistsError(email: String, deviceType:String) extends SurfsUpError
case class UserAuthenticationFailedError(email: String) extends SurfsUpError
case class RefreshTokenNotFoundError() extends SurfsUpError
case class RefreshTokenExpiredError() extends SurfsUpError
case class CouldNotUpdateUserDeviceError() extends SurfsUpError
case class CouldNotUpdatePasswordError() extends SurfsUpError
case class CouldNotUpdateUserError() extends SurfsUpError
case class OperationNotAllowed(message:String) extends SurfsUpError
case class UnknownError(message:String) extends SurfsUpError
case class WWError() extends SurfsUpError
