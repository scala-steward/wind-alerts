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

class UsersEndpoints[F[_] : Effect]
(userCredentialsService: UserCredentialService[F], userService: UserService[F], socialLoginService: SocialLoginService[F], userRolesService: UserRolesService[F], subscriptionsService: SocialPlatformSubscriptionsService[F])
  extends Http4sDsl[F] {

  def openEndpoints(): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case _@GET -> Root / "ping" => Ok("pong")

      case _@GET -> Root / "privacy-policy" =>
        Ok(statics.privacyPolicy)

      case _@GET -> Root / "about-surfs-up" =>
        Ok(statics.aboutSurfsUp)

      case req@POST -> Root => {
        (for {
          rr <- EitherT.liftF(req.as[RegisterRequest])
          user <- userService.register(rr)
        } yield user).value.flatMap {
          case Right(tokensWithUser) => Ok(TokensWithUserDTO.fromDomain(tokensWithUser))
          case Left(UserAlreadyExistsError(email, deviceType)) => Conflict(s"The user with email $email for device type $deviceType already exists")
        }
      }

      case req@POST -> Root / "login" => {
        (for {
          credentials <- EitherT.liftF(req.as[LoginRequest])
          user <- userService.login(credentials)
        } yield user).value.flatMap {
          case Right(tokensWithUser) => Ok(TokensWithUserDTO.fromDomain(tokensWithUser))
          case Left(UserNotFoundError(_)) => NotFound("User not found")
          case Left(UserAuthenticationFailedError(name)) => BadRequest(s"Authentication failed for user $name")
        }
      }

      case req@POST -> Root / "refresh" =>
        (for {
          refreshToken <- EitherT.liftF(req.as[AccessTokenRequest])
          user <- userService.refresh(refreshToken)
        } yield user).value.flatMap {
          case Right(tokensWithUser) => Ok(TokensWithUserDTO.fromDomain(tokensWithUser))
          case Left(RefreshTokenNotFoundError(_)) => BadRequest(s"Refresh token not found")
          case Left(RefreshTokenExpiredError(_)) => BadRequest(s"Refresh token expired")
          case Left(TokenNotFoundError(_)) => BadRequest(s"Token not found")
          case Left(UserNotFoundError(_)) => NotFound("User not found")
        }

      case req@POST -> Root / "changePassword" =>
        (for {
          changePasswordRequest <- EitherT.liftF(req.as[ChangePasswordRequest])
          user <- userCredentialsService.changePassword(changePasswordRequest)
        } yield user).value.flatMap {
          case Right(_) => Ok()
          case Left(UserAuthenticationFailedError(name)) => BadRequest(s"Authentication failed for user $name")
        }

      case req@POST -> Root / "resetPassword" =>
        (for {
          resetPasswordRequest <- EitherT.liftF(req.as[ResetPasswordRequest])
          user <- userCredentialsService.resetPassword(resetPasswordRequest.email, resetPasswordRequest.deviceType)
        } yield user).value.flatMap {
          case Right(_) => Ok()
          case Left(UserAuthenticationFailedError(name)) => BadRequest(s"Authentication failed for user $name")
          case Left(UserNotFoundError(_)) => NotFound("User not found")
        }

      case req@POST -> Root / "purchase" / "android" / "update" => {
        (for {
          update <- EitherT.liftF(req.as[AndroidUpdate])
          user <- userRolesService.handleAndroidUpdate(update)
        } yield user).value.flatMap {
          case Right(_) => Ok()
          case Left(error) => InternalServerError(error.getMessage)
        }
      }
    }


  def authedService(): AuthedRoutes[UserId, F] =
    AuthedRoutes {

      case authReq@PUT -> Root / "profile" as u => {
        OptionT.liftF(authReq.req.decode[UpdateUserRequest] {
          request =>
            (for {
              response <- userService.updateUserProfile(u.id, request.name, request.snoozeTill, request.disableAllAlerts, request.notificationsPerHour)
                .map(_.asDTO)
            } yield response).value.flatMap {
              case Right(response) => Ok(response)
              case Left(UserNotFoundError(_)) => NotFound("User not found")
            }
        })
      }

      case _@GET -> Root / "profile" as user => {
        OptionT.liftF(
          (for {
            response <- userService.getUser(user.id).map(_.asDTO())
          } yield response).value.flatMap {
            case Right(response) => Ok(response)
            case Left(UserNotFoundError(_)) => NotFound("User not found")
          }
        )
      }

      case authReq@PUT -> Root / "deviceToken" as user => {
        OptionT.liftF(authReq.req.decode[UpdateUserDeviceTokenRequest] {
          req =>
            (for {
              response <- userService.updateDeviceToken(user.id, req.deviceToken)
                .map(_.asDTO)
            } yield response).value.flatMap {
              case Right(response) => Ok(response)
              case Left(UserNotFoundError(_)) => NotFound("User not found")
            }
        })
      }

      case _@POST -> Root / "sendOTP" as user => {
        OptionT.liftF((for {
          response <- userService.sendOtp(user.id)
        } yield response).value.flatMap {
          case Right(_) => Ok()
          case Left(UserNotFoundError(_)) => NotFound("User not found")
        })
      }

      case authReq@POST -> Root / "verifyEmail" as user => {
        OptionT.liftF(authReq.req.decode[OTP] {
          req =>
            (for {
              response <- userRolesService.verifyEmail(user, req)
            } yield response).value.flatMap {
              case Right(response) => Ok(response)
              case Left(OtpNotFoundError(_)) => NotFound("Invalid or expired OTP")
              case Left(UserNotFoundError(_)) => NotFound("User not found")
            }
        })
      }

      case _@POST -> Root / "logout" as user => {
        OptionT.liftF({
          (for {
            response <- userService.logoutUser(user.id)
          } yield response).value.flatMap {
            case Right(response) => Ok()
            case Left(UserNotFoundError(_)) => NotFound("User not found")
          }
        })
      }

      case _@GET -> Root / "purchase" / "android" as user => {
        OptionT.liftF(
          (for {
            response <- userRolesService.getAndroidPurchase(user)
          } yield response).value.flatMap {
            case Right(response) => Ok(response)
            case Left(TokenNotFoundError(_)) => NotFound("Token not found")
            case Left(UserNotFoundError(_)) => NotFound("User not found")
          }
        )
      }

      case authReq@POST -> Root / "purchase" / "android" as user => {
        OptionT.liftF(authReq.req.decode[AndroidReceiptValidationRequest] {
          req =>
            (for {
              response <- subscriptionsService.updateAndroidPurchase(user, req)
            } yield response).value.flatMap {
              case Right(_) => Ok()
              case Left(TokenNotFoundError(_)) => NotFound("Token not found")
              case Left(UserNotFoundError(_)) => NotFound("User not found")
            }
        })
      }

      case _@GET -> Root / "purchase" / "apple" as user => {
        OptionT.liftF(
          (for {
            response <- userRolesService.updateAppleUser(user)
          } yield response).value.flatMap {
            case Right(response) => Ok(response)
            case Left(TokenNotFoundError(_)) => NotFound("Token not found")
            case Left(UserNotFoundError(_)) => NotFound("User not found")
          }
        )
      }

      case authReq@POST -> Root / "purchase" / "apple" as user => {
        OptionT.liftF(authReq.req.decode[ApplePurchaseToken] {
          req =>
            (for {
              response <- subscriptionsService.updateApplePurchase(user, req)
            } yield response).value.flatMap {
              case Right(response) => Ok()
              case Left(TokenNotFoundError(_)) => NotFound("Token not found")
              case Left(UserNotFoundError(_)) => NotFound("User not found")
            }
        })
      }
    }


  def facebookEndpoints(): HttpRoutes[F] = {

    HttpRoutes.of[F] {
      case req@POST -> Root => {
        (for {
          facebookRegisterRequest <- EitherT.liftF(req.as[FacebookRegisterRequest])
          tokensWithUser <- socialLoginService.registerOrLoginFacebookUser(facebookRegisterRequest.asDomain())
        } yield tokensWithUser).value.flatMap {
          case Right(tokensWithUser) => Ok(TokensWithUserDTO.fromDomain(tokensWithUser))
          case Left(UserAlreadyExistsError(email, deviceType)) => Conflict(s"The user with email $email for device type $deviceType already exists")
          case Left(UserNotFoundError(_)) => NotFound("User not found")
        }
      }

      case req@POST -> Root / "login" => {
        (for {
          facebookRegisterRequest <- EitherT.liftF(req.as[FacebookRegisterRequest])
          tokensWithUser <- socialLoginService.registerOrLoginFacebookUser(facebookRegisterRequest.asDomain())
        } yield tokensWithUser).value.flatMap {
          case Right(tokensWithUser) => Ok(TokensWithUserDTO.fromDomain(tokensWithUser))
          case Left(UserNotFoundError(_)) => NotFound("User not found")
          case Left(UserAuthenticationFailedError(name)) => BadRequest(s"Authentication failed for user $name")
        }
      }
    }
  }

  def appleEndpoints(): HttpRoutes[F] = {
    HttpRoutes.of[F] {
      case req@POST -> Root => {
        (for {
          appleRegisterRequest <- EitherT.liftF(req.as[AppleRegisterRequest])
          tokensWithUser <- socialLoginService.registerOrLoginAppleUser(appleRegisterRequest.asDomain())
        } yield tokensWithUser).value.flatMap {
          case Right(tokensWithUser) => Ok(TokensWithUserDTO.fromDomain(tokensWithUser))
          case Left(UserAlreadyExistsError(email, deviceType)) => Conflict(s"The user with email $email for device type $deviceType already exists")
          case Left(UserNotFoundError(_)) => NotFound("User not found")
        }
      }

      case req@POST -> Root / "login" =>
        (for {
          appleRegisterRequest <- EitherT.liftF(req.as[AppleRegisterRequest])
          tokensWithUser <- socialLoginService.registerOrLoginAppleUser(appleRegisterRequest.asDomain())
        } yield tokensWithUser).value.flatMap {
          case Right(tokensWithUser) => Ok(TokensWithUserDTO.fromDomain(tokensWithUser))
          case Left(UserAlreadyExistsError(email, deviceType)) => Conflict(s"The user with email $email for device type $deviceType already exists")
          case Left(UserNotFoundError(_)) => NotFound("User not found")
        }

    }

  }


}