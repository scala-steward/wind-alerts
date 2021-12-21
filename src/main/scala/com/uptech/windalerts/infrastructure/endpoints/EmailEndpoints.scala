package com.uptech.windalerts.infrastructure.endpoints


import cats.data.EitherT
import cats.effect.Effect
import cats.implicits._
import com.uptech.windalerts.core.{SurfsUpError, UnknownError}
import com.uptech.windalerts.core.otp.OTPService
import com.uptech.windalerts.infrastructure.endpoints.codecs._
import com.uptech.windalerts.infrastructure.endpoints.dtos.{UserRegistered, UserRegisteredUpdate}
import io.circe.parser.parse
import org.http4s.{HttpRoutes, Request}
import org.http4s.dsl.Http4sDsl
class EmailEndpoints[F[_] : Effect](otpService: OTPService[F]) extends Http4sDsl[F] {

  def allRoutes() = HttpRoutes.of[F] {
    case req@POST -> Root / "userRegistered" =>
      (for {
        userRegistered <- parseRequest(req)
        update <- otpService.send(userRegistered.userId.userId, userRegistered.emailId.email).leftWiden[SurfsUpError]
      } yield update).value.flatMap {
        case Right(_) => Ok()
        case Left(error) => InternalServerError(error.message)
      }
  }

  private def parseRequest(req: Request[F]) = {
    for {
      userRegisteredUpdate <- asUserRegisteredUpdate(req)
      decoded = new String(java.util.Base64.getDecoder.decode(userRegisteredUpdate.message.data))
      userRegistered <- asUserRegistered(decoded)
    } yield userRegistered
  }

  private def asUserRegisteredUpdate(req: Request[F]):EitherT[F, UnknownError, UserRegisteredUpdate] = {
    val eitherUserRegistered = EitherT.liftF(req.as[UserRegisteredUpdate])
    eitherUserRegistered.leftMap[UnknownError](e => UnknownError(e.toString))
  }

  private def asUserRegistered(response: String): EitherT[F, SurfsUpError, UserRegistered] = {
    EitherT.fromEither((for {
      parsed <- parse(response)
      decoded <- parsed.as[UserRegistered].leftWiden[io.circe.Error]
    } yield decoded).leftMap(error => UnknownError(error.getMessage)).leftWiden[SurfsUpError])
  }


}
