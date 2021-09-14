package com.uptech.windalerts.infrastructure.endpoints


import cats.data.{EitherT, OptionT}
import cats.effect.Effect
import cats.implicits._
import com.uptech.windalerts.core.{OtpNotFoundError, RefreshTokenExpiredError, RefreshTokenNotFoundError, TokenNotFoundError, UserAlreadyExistsError, UserAuthenticationFailedError, UserNotFoundError}
import com.uptech.windalerts.core.credentials.UserCredentialService
import com.uptech.windalerts.core.social.login.SocialLoginService
import com.uptech.windalerts.core.social.subscriptions.SocialPlatformSubscriptionsService
import com.uptech.windalerts.core.user.{UserId, UserRolesService, UserService}
import com.uptech.windalerts.config._
import codecs._
import dtos.{AppleRegisterRequest, ChangePasswordRequest, FacebookRegisterRequest, ResetPasswordRequest, _}
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, HttpRoutes}


import cats.data.EitherT
import com.uptech.windalerts.core.otp.OTPService
import com.uptech.windalerts.infrastructure.endpoints.codecs._
import com.uptech.windalerts.infrastructure.endpoints.dtos.UserRegisteredUpdate
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import codecs._
class EmailEndpoints[F[_] : Effect](otpService: OTPService[F]) extends Http4sDsl[F] {

  def allRoutes() = HttpRoutes.of[F] {
    case req@POST -> Root / "userRegistered" => {
      (for {
        userRegistered <- EitherT.liftF(req.as[UserRegisteredUpdate])
        update <- otpService.handleUserRegistered(userRegistered)
      } yield update).value.flatMap {
        case Right(_) => Ok()
        case Left(error) => InternalServerError(error.getMessage)
      }
    }
  }
}
