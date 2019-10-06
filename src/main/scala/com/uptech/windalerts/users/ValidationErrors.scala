package com.uptech.windalerts.users

import com.uptech.windalerts.domain.domain.User

sealed trait ValidationError extends Product with Serializable
case class UserNotFoundError() extends ValidationError
case class UserAlreadyExistsError(email: String, deviceType:String) extends ValidationError
case class UserAuthenticationFailedError(email: String) extends ValidationError