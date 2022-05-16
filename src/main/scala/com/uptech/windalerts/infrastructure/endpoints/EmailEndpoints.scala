package com.uptech.windalerts.infrastructure.endpoints


import cats.Applicative
import cats.effect.Effect
import cats.implicits._
import cats.mtl.implicits.toHandleOps
import com.uptech.windalerts.core.otp.OTPService
import com.uptech.windalerts.core.types.{UserRegistered, UserRegisteredUpdate}
import com.uptech.windalerts.infrastructure.endpoints.codecs._
import com.uptech.windalerts.infrastructure.endpoints.errors.mapError
import io.circe.parser.parse
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Request}

class EmailEndpoints[F[_] : Effect](otpService: OTPService[F]) extends Http4sDsl[F] {

  def allRoutes() = HttpRoutes.of[F] {
    case req@POST -> Root / "userRegistered" =>
      (for {
        userRegistered <- parseRequest(req)
        update <- otpService.send(userRegistered.userId.userId, userRegistered.emailId.email)
      } yield update).flatMap(
        Ok(_)
      ).handle[Throwable](mapError(_))
  }

  private def parseRequest(req: Request[F]) = {
    for {
      userRegisteredUpdate <- asUserRegisteredUpdate(req)
      decoded = new String(java.util.Base64.getDecoder.decode(userRegisteredUpdate.message.data))
      userRegistered <- asUserRegistered(decoded)
    } yield userRegistered
  }

  private def asUserRegisteredUpdate(req: Request[F]):F[UserRegisteredUpdate] = {
    req.as[UserRegisteredUpdate]
  }

  private def asUserRegistered(response: String): F[UserRegistered] = {
    Applicative[F].pure(((for {
      parsed <- parse(response)
      decoded <- parsed.as[UserRegistered]
    } yield decoded).toOption.get))
  }


}
