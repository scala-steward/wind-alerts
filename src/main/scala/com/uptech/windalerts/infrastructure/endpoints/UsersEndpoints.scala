package com.uptech.windalerts.infrastructure.endpoints

import cats.Applicative
import cats.data.OptionT
import cats.effect.{Effect, Sync}
import cats.implicits._
import cats.mtl.Handle
import cats.mtl.implicits.toHandleOps
import com.uptech.windalerts.config._
import com.uptech.windalerts.core.Infrastructure
import com.uptech.windalerts.core.otp.OTPService
import com.uptech.windalerts.core.social.login.SocialLoginService
import com.uptech.windalerts.core.social.subscriptions.SubscriptionService
import com.uptech.windalerts.core.types._
import com.uptech.windalerts.core.user.credentials.UserCredentialService
import com.uptech.windalerts.core.user.sessions.UserSessions
import com.uptech.windalerts.core.user.{TokensWithUser, UserIdMetadata, UserRolesService, UserService}
import com.uptech.windalerts.infrastructure.endpoints.codecs._
import com.uptech.windalerts.infrastructure.endpoints.errors.mapError
import com.uptech.windalerts.infrastructure.social.SocialPlatformTypes.{Apple, Facebook, Google}
import com.uptech.windalerts.infrastructure.social.login.AccessRequests.{AppleRegisterRequest, FacebookRegisterRequest}
import io.circe.parser.parse
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.typelevel.ci.CIString

class UsersEndpoints[F[_] : Effect](  userRolesService: UserRolesService[F], subscriptionService: SubscriptionService[F], otpService: OTPService[F])(implicit infrastructure: Infrastructure[F], FR: Handle[F, Throwable])
  extends Http4sDsl[F] {

  def openEndpoints(): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case _@GET -> Root / "ping" => Ok("pong")

      case _@GET -> Root / "privacy-policy" =>
        Ok(statics.privacyPolicy)

      case _@GET -> Root / "about-surfs-up" =>
        Ok(statics.aboutSurfsUp)

      case req@POST -> Root =>
        (for {
          rr <- req.as[RegisterRequest]
          user <- UserService.register(rr)
        } yield user).flatMap(Ok(_)
        ).handle[Throwable](mapError(_))

      case req@POST -> Root / "login" =>
        (for {
          credentials <- req.as[LoginRequest]
          user <- UserService.login(credentials)
        } yield user).flatMap(Ok(_)
        ).handle[Throwable](mapError(_))

      case req@POST -> Root / "refresh" =>
        (for {
          refreshToken <- req.as[AccessTokenRequest]
          user <- UserService.refresh(refreshToken)
        } yield user).flatMap(
          Ok(_)
        ).handle[Throwable](mapError(_))


      case req@POST -> Root / "changePassword" =>
        (for {
          changePasswordRequest <- req.as[ChangePasswordRequest]
          user <- UserCredentialService.changePassword(changePasswordRequest)
        } yield user).flatMap(Ok(_)
        ).handle[Throwable](mapError(_))

      case req@POST -> Root / "resetPassword" =>
        (for {
          resetPasswordRequest <- req.as[ResetPasswordRequest]
          user <- UserService.resetPassword(resetPasswordRequest.email, resetPasswordRequest.deviceType)
        } yield user).flatMap(_ => Ok())
          .handle[Throwable](mapError(_))

      case req@POST -> Root / "purchase" / "android" / "update" =>
        (for {
          update <- req.as[AndroidUpdate]
          decoded = new String(java.util.Base64.getDecoder.decode(update.message.data))
          subscription <- asSubscription(decoded)
          user <- userRolesService.handleUpdate("Google", subscription.subscriptionNotification.purchaseToken)
        } yield user).flatMap(_ => Ok())
          .handle[Throwable](mapError(_))
    }


  private def asSubscription(response: String): F[SubscriptionNotificationWrapper] = {
    Applicative[F].pure((for {
      parsed <- parse(response)
      decoded <- parsed.as[SubscriptionNotificationWrapper]
    } yield decoded).toOption.get)
  }


  def authedService(): AuthedRoutes[UserIdMetadata, F] =
    AuthedRoutes {

      case authReq@PUT -> Root / "profile" as u =>
        OptionT.liftF(authReq.req.decode[UpdateUserRequest] {
          request =>
            (for {
              response <- UserService.updateUserProfile(u.userId.id, request.name, request.snoozeTill, request.disableAllAlerts, request.notificationsPerHour)
            } yield response).flatMap(_ => Ok())
              .handle[Throwable](mapError(_))
        })

      case _@GET -> Root / "profile" as user =>
        OptionT.liftF(
          (for {
            response <- UserService.getUser(user.userId.id)
          } yield response).flatMap(_ => Ok())
            .handle[Throwable](mapError(_))
        )

      case authReq@PUT -> Root / "deviceToken" as user =>
        OptionT.liftF(authReq.req.decode[UpdateUserDeviceTokenRequest] {
          req =>
            (for {
              response <- UserSessions.updateDeviceToken(user.userId.id, req.deviceToken)
            } yield response).flatMap(_ => Ok())
              .handle[Throwable](mapError(_))
        })

      case _@POST -> Root / "sendOTP" as user =>
        OptionT.liftF({
          (for {
            response <- otpService.send(user.userId.id, user.emailId.email)
          } yield response).flatMap(Ok(_))
            .handle[Throwable](mapError(_))
        })

      case authReq@POST -> Root / "verifyEmail" as user =>
        OptionT.liftF(authReq.req.decode[OTP] {
          req =>
            (for {
              response <- userRolesService.verifyEmail(user.userId, req)
            } yield response).flatMap(Ok(_))
              .handle[Throwable](mapError(_))
        })

      case _@POST -> Root / "logout" as user =>
        OptionT.liftF({
          (for {
            response <- UserService.logout(user.userId.id)
          } yield response).flatMap(_ => Ok())
            .handle[Throwable](mapError(_))
        })

      case _@GET -> Root / "purchase" / "android" as user =>
        OptionT.liftF(
          (for {
            response <- userRolesService.updateUserPurchase(user.userId)
          } yield response).flatMap(Ok(_))
            .handle[Throwable](mapError(_))
        )

      case authReq@POST -> Root / "purchase" / "android" as user =>
        OptionT.liftF(authReq.req.decode[PurchaseReceiptValidationRequest] {
          req =>
            (for {
              response <- subscriptionService.handleNewPurchase(user.userId, req, Google)
            } yield response).flatMap(_ => Ok())
              .handle[Throwable](mapError(_))
        })

      case _@GET -> Root / "purchase" / "apple" as user =>
        OptionT.liftF(
          (for {
            response <- userRolesService.updateUserPurchase(user.userId)
          } yield response).flatMap(Ok(_))
            .handle[Throwable](mapError(_))
        )

      case authReq@POST -> Root / "purchase" / "apple" as user =>
        OptionT.liftF(authReq.req.decode[PurchaseReceiptValidationRequest] {
          req =>
            (for {
              response <- subscriptionService.handleNewPurchase(user.userId, req, Apple)
            } yield response).flatMap(_ => Ok())
              .handle[Throwable](mapError(_))
        })
    }


  def facebookEndpoints()(implicit F: Sync[F]): HttpRoutes[F] = {

    HttpRoutes.of[F] {
      case req@POST -> Root =>
        (for {
          facebookRegisterRequest <- req.as[FacebookRegisterRequest]
          tokensWithUser <- SocialLoginService.registerOrLoginSocialUser(
            Facebook,
            facebookRegisterRequest.accessToken,
            facebookRegisterRequest.deviceType,
            facebookRegisterRequest.deviceToken,
            None)
        } yield tokensWithUser).flatMap(handleRegisterOrLoginResponse(F, _)
        ).handle[Throwable](mapError(_))


      case req@POST -> Root / "login" => {
        (for {
          facebookRegisterRequest <- req.as[FacebookRegisterRequest]
          tokensWithUser <- SocialLoginService.registerOrLoginSocialUser(
            Facebook,
            facebookRegisterRequest.accessToken,
            facebookRegisterRequest.deviceType,
            facebookRegisterRequest.deviceToken,
            None)
        } yield tokensWithUser).flatMap(handleRegisterOrLoginResponse(F, _)
        ).handle[Throwable](mapError(_))
      }
    }
  }

  def appleEndpoints()(implicit F: Sync[F]): HttpRoutes[F] = {
    HttpRoutes.of[F] {
      case req@POST -> Root => {
        (for {
          appleRegisterRequest <- req.as[AppleRegisterRequest]
          tokensWithUser <- SocialLoginService.registerOrLoginSocialUser(
            Apple,
            appleRegisterRequest.authorizationCode,
            appleRegisterRequest.deviceType,
            appleRegisterRequest.deviceToken,
            Some(appleRegisterRequest.name)
          )
        } yield tokensWithUser).flatMap(handleRegisterOrLoginResponse(F, _)
        ).handle[Throwable](mapError(_))
      }

      case req@POST -> Root / "login" =>
        (for {
          appleRegisterRequest <- req.as[AppleRegisterRequest]
          tokensWithUser <- SocialLoginService.registerOrLoginSocialUser(
            Apple, appleRegisterRequest.authorizationCode,
            appleRegisterRequest.deviceType,
            appleRegisterRequest.deviceToken,
            Some(appleRegisterRequest.name)
          )
        } yield tokensWithUser).flatMap(handleRegisterOrLoginResponse(F, _)
        ).handle[Throwable](mapError(_))

    }
  }

  private def handleRegisterOrLoginResponse(F: Sync[F], tokensWithUser: (TokensWithUser, Boolean)) = {
    F.pure(
      Response[F](headers = Headers {
        Header.Raw(CIString("X-is-new-user"), tokensWithUser._2.toString)
      }).withEntity(tokensWithUser._1)
    )
  }


}