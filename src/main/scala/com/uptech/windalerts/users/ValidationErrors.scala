package com.uptech.windalerts.users

sealed trait ValidationError extends Product with Serializable
case class UserNotFoundError() extends ValidationError
case class UserAlreadyExistsError(email: String, deviceType:String) extends ValidationError
case class UserAuthenticationFailedError(email: String) extends ValidationError
case class RefreshTokenNotFoundError() extends ValidationError
case class RefreshTokenExpiredError() extends ValidationError
case class CouldNotUpdateUserDeviceError() extends ValidationError