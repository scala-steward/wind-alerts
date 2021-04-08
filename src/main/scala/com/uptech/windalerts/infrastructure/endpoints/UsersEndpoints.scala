package com.uptech.windalerts.infrastructure.endpoints

import cats.data.EitherT
import cats.effect.Effect
import cats.implicits._
import com.uptech.windalerts.Repos
import com.uptech.windalerts.core.credentials.UserCredentialService
import com.uptech.windalerts.core.feedbacks.Feedback
import com.uptech.windalerts.core.social.login.SocialLoginService
import com.uptech.windalerts.core.social.subscriptions.SubscriptionsService
import com.uptech.windalerts.core.user.{UserRolesService, UserService, UserT}
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain.{AppleRegisterRequest, ChangePasswordRequest, FacebookRegisterRequest, ResetPasswordRequest, _}
import com.uptech.windalerts.domain.{SurfsUpError, UserNotFoundError, statics}
import org.http4s.{AuthedRoutes, HttpRoutes}
import com.uptech.windalerts.domain.domain.{AppleRegisterRequest, ChangePasswordRequest, FacebookRegisterRequest, ResetPasswordRequest, UserDTO, _}
import com.uptech.windalerts.domain.{SurfsUpError, UnknownError, UserNotFoundError, secrets, statics}
import com.uptech.windalerts.users._
import io.circe.parser._
import org.http4s.{AuthedRoutes, HttpRoutes, Request}
import org.log4s.getLogger

class UsersEndpoints[F[_] : Effect]
(repos: Repos[F], userCredentialsService: UserCredentialService[F], userService: UserService[F], socialLoginService: SocialLoginService[F], userRolesService: UserRolesService[F], subscriptionsService: SubscriptionsService[F], httpErrorHandler: HttpErrorHandler[F]) extends http[F](httpErrorHandler) {

  def openEndpoints(): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case _@GET -> Root / "ping" =>
        handle(() => EitherT.pure("pong"))

      case _@GET -> Root / "privacy-policy" =>
        handle(() => EitherT.pure(statics.privacyPolicy))

      case _@GET -> Root / "about-surfs-up" =>
        handle(() => EitherT.pure(statics.aboutSurfsUp))

      case req@POST -> Root =>
        val rr = req.as[RegisterRequest]
        handle(rr, userService.register(_))

      case req@POST -> Root / "login" =>
        val credentials = req.as[LoginRequest]
        handle(credentials, userService.login(_))

      case req@POST -> Root / "refresh" =>
        val refreshToken = req.as[AccessTokenRequest]
        handle(refreshToken, userService.refresh(_))

      case req@POST -> Root / "changePassword" =>
        val changePasswordRequest = req.as[ChangePasswordRequest]
        handle(changePasswordRequest, userCredentialsService.changePassword(_))

      case req@POST -> Root / "resetPassword" =>
        val resetPasswordRequest = req.as[ResetPasswordRequest]
        handle(resetPasswordRequest, (x: ResetPasswordRequest) => {
          userCredentialsService.resetPassword(x.email, x.deviceType).map(_ => ())
        })

      case req@POST -> Root / "purchase" / "android" / "update" => {
        val action: EitherT[F, SurfsUpError, UserT] =
          for {
            update <- EitherT.liftF(req.as[AndroidUpdate])
            user <- userRolesService.handleAndroidUpdate(update)
          } yield user

        action.value.flatMap {
          case Right(_) => Ok()
          case Left(error) => httpErrorHandler.handleThrowable(error)
        }
      }
    }


  def authedService(): AuthedRoutes[UserId, F] =
    AuthedRoutes {

      case authReq@PUT -> Root / "profile" as user => {
        getLogger.error(s"Updating ${user.id} profile")
        handleOk(authReq, user, (u: UserId, request: UpdateUserRequest) =>
          userService.updateUserProfile(u.id, request.name, request.snoozeTill, request.disableAllAlerts, request.notificationsPerHour)
            .map(_.asDTO)
        )
      }

      case _@GET -> Root / "profile" as user => {
        handleOkNoDecode(user, (u: UserId) => {
          repos.usersRepo().getByUserIdEitherT(u.id).map(_.asDTO()).leftMap(_ => UserNotFoundError())
        }
        )
      }

      case authReq@PUT -> Root / "deviceToken" as user => {
        getLogger.error(s"Updating ${user.id} token")
        handleOk(authReq, user, (u: UserId, request: UpdateUserDeviceTokenRequest) =>
          userService.updateDeviceToken(u.id, request.deviceToken)
            .map(_.asDTO)
        )
      }

      case _@POST -> Root / "sendOTP" as user => {
        handleOkNoDecode(user, (u: UserId) => userService.sendOtp(user.id))
      }

      case authReq@POST -> Root / "verifyEmail" as user => {
        handleOk(authReq, user, (_: UserId, request: OTP) =>
          userRolesService.verifyEmail(user, request)
        )
      }

      case _@POST -> Root / "logout" as user => {
        getLogger.error(s"logout ${user.id}")
        handleOkNoDecode(user, (u: UserId) => userService.logoutUser(user.id))
      }

      case authReq@POST -> Root / "feedbacks" as user => {
        handleEmptyOk(authReq, user, (u: UserId, request: FeedbackRequest) =>
          userService.createFeedback(Feedback(request.topic, request.message, user.id))
        )
      }

      case _@GET -> Root / "purchase" / "android" as user => {
        handleOkNoDecode(user, (u: UserId) => userRolesService.getAndroidPurchase(u))
      }

      case authReq@POST -> Root / "purchase" / "android" as user => {
        handleEmptyOk(authReq, user, (u: UserId, request: AndroidReceiptValidationRequest) =>
          subscriptionsService.getAndroidPurchase(user, request)
        )
      }

      case _@GET -> Root / "purchase" / "apple" as user => {
        handleOkNoDecode(user, (u: UserId) => userRolesService.updateAppleUser(user))
      }

      case authReq@POST -> Root / "purchase" / "apple" as user => {
        handleEmptyOk(authReq, user, (u: UserId, req: ApplePurchaseToken) =>
          subscriptionsService.updateApplePurchase(user, req)
        )
      }
    }



  def facebookEndpoints(): HttpRoutes[F] = {

    HttpRoutes.of[F] {
      case req@POST -> Root => {
        val facebookRegisterRequest = req.as[FacebookRegisterRequest]
        handle(facebookRegisterRequest.map(_.asDomain()), socialLoginService.registerOrLoginFacebookUser(_))
      }

      case req@POST -> Root / "login" =>
        val facebookLoginRequest = req.as[FacebookRegisterRequest]
        handle(facebookLoginRequest.map(_.asDomain()), socialLoginService.registerOrLoginFacebookUser(_))
    }
  }

  def appleEndpoints(): HttpRoutes[F] = {
    HttpRoutes.of[F] {
      case req@POST -> Root =>
        val appleRegisterRequest = req.as[AppleRegisterRequest]
        handle(appleRegisterRequest.map(_.asDomain()), socialLoginService.registerOrLoginAppleUser(_))

      case req@POST -> Root / "login" =>
        val appleLoginRequest = req.as[AppleRegisterRequest]
        handle(appleLoginRequest.map(_.asDomain()), socialLoginService.registerOrLoginAppleUser(_))
    }

  }
}