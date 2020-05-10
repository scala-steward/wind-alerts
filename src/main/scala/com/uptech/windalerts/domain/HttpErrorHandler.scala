package com.uptech.windalerts.domain


import cats.Monad
import com.uptech.windalerts.users._
import org.http4s.Response
import org.http4s.dsl.Http4sDsl
import org.log4s.getLogger


class HttpErrorHandler[F[_] : Monad] extends Http4sDsl[F] {
  val handleThrowable: Throwable => F[Response[F]] = {
    case e: ValidationError => {
      getLogger.error(e)(e.getMessage)
      handleError(e)
    }
    case e: errors.OperationNotPermitted => {
      getLogger.error(e)(e.getMessage)
      Forbidden(e.message)
    }
    case e: errors.RecordNotFound => {
      getLogger.error(e)(e.getMessage)
      NotFound(e.message)
    }
    case e: errors.HeaderNotPresent => {
      getLogger.error(e)(e.getMessage)
      BadRequest(e.message)
    }
    case e: errors.UserAlreadyRegistered => {
      getLogger.error(e)(e.getMessage)
      Conflict(e.message)
    }
    case everythingElse => {
      getLogger.error(everythingElse)(everythingElse.getMessage)
      InternalServerError(everythingElse.toString)
    }
  }

  val handleError: ValidationError => F[Response[F]] = {
    case e@UserAlreadyExistsError(email, deviceType) => {
      getLogger.error(e)(e.getMessage)
      Conflict(s"The user with email $email for device type $deviceType already exists")
    }
    case e@UserAuthenticationFailedError(name) => {
      getLogger.error(e)(e.getMessage)
      BadRequest(s"Authentication failed for user $name")
    }
    case e@RefreshTokenNotFoundError() => {
      getLogger.error(e)(e.getMessage)
      BadRequest(s"Refresh token not found")
    }
    case e@RefreshTokenExpiredError() => {
      getLogger.error(e)(e.getMessage)
      BadRequest(s"Refresh token expired")
    }
    case e@AlertNotFoundError() => {
      getLogger.error(e)(e.getMessage)
      NotFound("Alert not found")
    }
    case e@OperationNotAllowed(message) => {
      getLogger.error(e)(e.getMessage)
      Forbidden(message)
    }
    case e@OtpNotFoundError() => {
      getLogger.error(e)(e.getMessage)
      Forbidden("Invalid or expired OTP")
    }
    case e@everythingElse => {
      getLogger.error(e)(e.getMessage)
      InternalServerError(everythingElse.toString)
    }
  }

}
