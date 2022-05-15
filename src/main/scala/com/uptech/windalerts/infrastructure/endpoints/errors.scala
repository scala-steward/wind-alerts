package com.uptech.windalerts.infrastructure.endpoints

import cats.Applicative
import cats.data.Kleisli
import cats.effect.{IO, Sync}
import cats.implicits._
import com.uptech.windalerts.core.{OtpNotFoundError, RefreshTokenExpiredError, RefreshTokenNotFoundError, TokenNotFoundError, UserAuthenticationFailedError, UserNotFoundError}
import com.uptech.windalerts.logger
import fs2.Stream
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpApp, HttpRoutes, Response, Status}

object errors {

  def mapError[F[_]](t:Throwable):Response[F] = t match {
    case _@RefreshTokenNotFoundError(msg) =>
      Response(status = Status.BadRequest).withBodyStream(Stream.emits(msg.getBytes()))
    case _@RefreshTokenExpiredError(msg) =>
      Response(status = Status.BadRequest).withBodyStream(Stream.emits(msg.getBytes()))
    case _@TokenNotFoundError(msg) =>
      Response(status = Status.BadRequest).withBodyStream(Stream.emits(msg.getBytes()))
    case _@UserNotFoundError(msg) =>
      Response(status = Status.NotFound).withBodyStream(Stream.emits(msg.getBytes()))
    case _@OtpNotFoundError(_) =>
      Response(status = Status.NotFound).withBodyStream(Stream.emits("Invalid or expired OTP".getBytes()))
    case _@UserAuthenticationFailedError(name) =>
      Response(status = Status.BadRequest).withBodyStream(Stream.emits(s"Authentication failed for user $name".getBytes()))
    case e@_ =>
      Response(status = Status.InternalServerError).withBodyStream(Stream.emits(s"${e.getMessage()}".getBytes()))
  }

  def errorMapper(service: HttpApp[IO]): HttpApp[IO] = Kleisli { req =>
    service(req).map {
      case Status.Successful(resp) => {
        resp
      }
      case resp => {
        resp.withEntity(resp.bodyText.map(s => {
          logger.warn(s)
          if (s.equals("not found")) "Invalid access token" else s
        }))
      }
    }
  }
}

class errors[F[_] : Sync]() extends Http4sDsl[F] {
  def errorMapper(service: HttpApp[F])(implicit M: Applicative[F]): HttpApp[F] = Kleisli { req =>
    service(req).map {
      case Status.Successful(resp) => resp
      case resp => {
        resp.withEntity(resp.bodyText.map(s => {
          logger.warn(s)
          if (s.equals("not found")) "Invalid access token" else s
        }))
      }
    }
  }
}
