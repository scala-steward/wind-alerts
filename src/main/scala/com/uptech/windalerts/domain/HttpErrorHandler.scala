package com.uptech.windalerts.domain


import java.lang.RuntimeException

import cats.Monad
import com.google.firebase.auth.FirebaseAuthException
import com.uptech.windalerts.users.{RefreshTokenExpiredError, RefreshTokenNotFoundError, UserAlreadyExistsError, UserAuthenticationFailedError, ValidationError}
import org.http4s.{Response, Status}
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io.{BadRequest, Conflict}

class HttpErrorHandler[F[_] : Monad] extends Http4sDsl[F] {
  val handleThrowable: Throwable => F[Response[F]] = {

    case e: FirebaseAuthException => BadRequest(e.getErrorCode)
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
      BadRequest(s"Request token not found")
    case RefreshTokenExpiredError() =>
      BadRequest(s"Refresh token expired")
    case everythingElse => InternalServerError(everythingElse.toString)
  }

}
