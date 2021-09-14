package com.uptech.windalerts.infrastructure.endpoints


import cats.data.EitherT
import cats.effect.Effect
import cats.implicits._
import com.uptech.windalerts.core.otp.OTPService
import com.uptech.windalerts.infrastructure.endpoints.codecs._
import com.uptech.windalerts.infrastructure.endpoints.dtos.{UserRegistered, UserRegisteredUpdate}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
class EmailEndpoints[F[_] : Effect](otpService: OTPService[F]) extends Http4sDsl[F] {

  def allRoutes() = HttpRoutes.of[F] {
    case req@POST -> Root / "userRegistered" => {
      (for {
        userRegistered <- EitherT.liftF(req.as[UserRegistered])
        handled <- otpService.handleUserRegistered(userRegistered)
      } yield handled).value.flatMap {
        case Right(_) => Ok()
        case Left(error) => InternalServerError(error.getMessage)
      }
    }
  }
}
