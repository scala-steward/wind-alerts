package com.uptech.windalerts.domain


import cats.Monad
import com.uptech.windalerts.users._
import org.http4s.Response
import org.http4s.dsl.Http4sDsl

class HttpErrorHandler[F[_] : Monad] extends Http4sDsl[F] {
  val handleThrowable: Throwable => F[Response[F]] = {

    case e: errors.OperationNotPermitted => Forbidden(e.message)
    case e: errors.RecordNotFound => NotFound(e.message)
    case e: errors.HeaderNotPresent => BadRequest(e.message)
    case e: errors.UserAlreadyRegistered => Conflict(e.message)
    case everythingElse => InternalServerError(everythingElse.toString)
  }

  val handleError: ValidationError => F[Response[F]] = {
    case UserAlreadyExistsError(email, deviceType) =>
      Conflict(s"The user with email $email for device type $deviceType already exists")
    case UserAuthenticationFailedError(name) =>
      BadRequest(s"Authentication failed for user $name")
    case RefreshTokenNotFoundError() =>
      BadRequest(s"Refresh token not found")
    case RefreshTokenExpiredError() =>
      BadRequest(s"Refresh token expired")
    case OperationNotAllowed(message) =>
      Forbidden(message)
    case OtpNotFoundError() =>
      Forbidden("Invalid or expired OTP")
    case everythingElse => InternalServerError(everythingElse.toString)
  }

}
