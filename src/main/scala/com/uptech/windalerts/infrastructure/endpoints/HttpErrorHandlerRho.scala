package com.uptech.windalerts.infrastructure.endpoints

import cats.Monad
import cats.effect.Effect
import com.uptech.windalerts.domain._
import org.http4s.Response
import org.http4s.dsl.Http4sDsl
import org.http4s.rho.Result.BaseResult
import org.http4s.rho.RhoRoutes
import org.log4s.getLogger


class HttpErrorHandlerRho[F[+_]: Effect] extends RhoRoutes[F] {

  val handleThrowable: Throwable => F[BaseResult[F]] = {
    case e: SurfsUpError => {
      handleError(e)
    }
    case everythingElse => {
      getLogger.error(everythingElse)(everythingElse.getMessage)
      InternalServerError(everythingElse.toString)
    }
  }

  val handleError: SurfsUpError => F[BaseResult[F]] = {
    case e@UserAlreadyExistsError(email, deviceType) => {
      getLogger.error(e)(e.getMessage)
      Conflict(s"The user with email $email for device type $deviceType already exists")
    }
    case e@UserAuthenticationFailedError(name) => {
      getLogger.error(e)(e.getMessage)
      BadRequest(s"Authentication failed for user $name")
    }
    case e@RefreshTokenNotFoundError(_) => {
      getLogger.error(e)(e.getMessage)
      BadRequest(s"Refresh token not found")
    }
    case e@TokenNotFoundError(_) => {
      getLogger.error(e)(e.getMessage)
      BadRequest(s"Token not found")
    }
    case e@TokenExpiredError(_) => {
      getLogger.error(e)(e.getMessage)
      BadRequest(s"Token expired")
    }
    case e@RefreshTokenExpiredError(_) => {
      getLogger.error(e)(e.getMessage)
      BadRequest(s"Refresh token expired")
    }
    case e@AlertNotFoundError(_) => {
      getLogger.error(e)(e.getMessage)
      NotFound("Alert not found")
    }
    case e@OperationNotAllowed(message) => {
      getLogger.error(e)(e.getMessage)
      Forbidden(message)
    }
    case e@OtpNotFoundError(_) => {
      getLogger.error(e)(e.getMessage)
      Forbidden("Invalid or expired OTP")
    }
    case e@UserNotFoundError(_) => {
      getLogger.error(e)(e.getMessage)
      NotFound("User not found")
    }
    case e@WWError(_) => {
      getLogger.error(e)(e.getMessage)
      InternalServerError("Error while fetching beach status")
    }
    case e@everythingElse => {
      getLogger.error(e)(e.getMessage)
      InternalServerError(everythingElse.toString)
    }
  }

}
