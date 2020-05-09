package com.uptech.windalerts.domain


import cats.Monad
import com.uptech.windalerts.users._
import org.http4s.Response
import org.http4s.dsl.Http4sDsl
import org.log4s.getLogger


class HttpErrorHandler[F[_] : Monad] extends Http4sDsl[F] {
  val handleThrowable: Throwable => F[Response[F]] = {
    case e: ValidationError => {
      getLogger.error(e)
      handleError(e)
    }
    case e: errors.OperationNotPermitted => {
      getLogger.error(e)
      Forbidden(e.message)
    }
    case e: errors.RecordNotFound => {
      getLogger.error(e)
      NotFound(e.message)
    }
    case e: errors.HeaderNotPresent => {
      getLogger.error(e)
      BadRequest(e.message)
    }
    case e: errors.UserAlreadyRegistered => {
      getLogger.error(e)
      Conflict(e.message)
    }
    case everythingElse => {
      getLogger.error(everythingElse)
      InternalServerError(everythingElse.toString)
    }
  }

  val handleError: ValidationError => F[Response[F]] = {
    case e@UserAlreadyExistsError(email, deviceType) => {
      getLogger.error(e)
      Conflict(s"The user with email $email for device type $deviceType already exists")
    }
    case e@UserAuthenticationFailedError(name) => {
      getLogger.error(e)
      BadRequest(s"Authentication failed for user $name")
    }
    case e@RefreshTokenNotFoundError() => {
      getLogger.error(e)
      BadRequest(s"Refresh token not found")
    }
    case e@RefreshTokenExpiredError() => {
      getLogger.error(e)
      BadRequest(s"Refresh token expired")
    }
    case e@AlertNotFoundError() => {
      getLogger.error(e)
      NotFound("Alert not found")
    }
    case e@OperationNotAllowed(message) => {
      getLogger.error(e)
      Forbidden(message)
    }
    case e@OtpNotFoundError() => {
      getLogger.error(e)
      Forbidden("Invalid or expired OTP")
    }
    case e@everythingElse => {
      getLogger.error(e)
      InternalServerError(everythingElse.toString)
    }
  }

}
