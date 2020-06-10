package com.uptech.windalerts.users

import cats.data.{EitherT, OptionT}
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.Repos
import com.uptech.windalerts.domain._
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain.{ChangePasswordRequest, ResetPasswordRequest, _}
import io.circe.parser._
import io.scalaland.chimney.dsl._
import org.http4s.{AuthedRoutes, HttpRoutes, Response}

class UsersEndpoints(repos: Repos[IO],
                     userService: UserService[IO],
                     userRolesService: UserRolesService[IO],
                     subscriptionsService: SubscriptionsService[IO],
                     httpErrorHandler: HttpErrorHandler[IO],
                     auth: AuthenticationService[IO],
                     otpService:OTPService[IO])(implicit cs: ContextShift[IO])  extends http[IO](httpErrorHandler) {

  def openEndpoints(): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case r@GET -> Root / "ping"  =>
        handle(() => EitherT.liftF(IO("pong")))

      case _@GET -> Root / "privacy-policy"  =>
        handle(() => EitherT.liftF(IO(statics.privacyPolicy)))

      case _@GET -> Root / "about-surfs-up"  =>
        handle(() => EitherT.liftF(IO(statics.aboutSurfsUp)))

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
        handle(changePasswordRequest, userService.changePassword(_))

      case req@POST -> Root / "resetPassword" =>
        val resetPasswordRequest = req.as[ResetPasswordRequest]
        handle(resetPasswordRequest, (x:ResetPasswordRequest) => {
          userService.resetPassword(x.email, x.deviceType).map(_=>())
        })

      case req@POST -> Root / "purchase" / "android" / "update" => {
        val action: EitherT[IO, SurfsUpError, UserT] = for {
          update <- EitherT.liftF(req.as[AndroidUpdate])
          decoded <- EitherT.liftF(IO(new String(java.util.Base64.getDecoder.decode(update.message.data))))
          subscription <- asSubscription(decoded)
          token <- repos.androidPurchaseRepo().getPurchaseByToken(subscription.subscriptionNotification.purchaseToken)
          purchase <- subscriptionsService.getAndroidPurchase(token.subscriptionId, subscription.subscriptionNotification.purchaseToken)
          user <- userService.getUser(token.userId)
          updatedUser <- userRolesService.updateSubscribedUserRole(user, purchase.startTimeMillis, purchase.expiryTimeMillis)
        } yield updatedUser
        action.value.flatMap {
          case Right(_) => Ok()
          case Left(error) => httpErrorHandler.handleThrowable(error)
        }
      }
    }

  def authedService(): AuthedRoutes[UserId, IO] =
    AuthedRoutes {
      case authReq@PUT -> Root / "profile" as user => {
        val response: IO[Response[IO]] = authReq.req.decode[UpdateUserRequest] { request =>
          val action = for {
            updateResult <- userService.updateUserProfile(user.id, request.name, request.snoozeTill, request.disableAllAlerts, request.notificationsPerHour)
          } yield updateResult
          action.value.flatMap {
            case Right(tokens) => Ok(tokens.into[UserDTO].withFieldComputed(_.id, u => u._id.toHexString).transform)
            case Left(error) => httpErrorHandler.handleError(error)
          }
        }
        OptionT.liftF(response)
      }

      case _@POST -> Root / "sendOTP" as user => {
        val action = for {
          userFromDb <- userService.getUser(user.id)
          sent <- otpService.send(userFromDb._id.toHexString, userFromDb.email)
        } yield sent
        val response = action.value.flatMap {
          case Right(_) => Ok()
          case Left(error) => httpErrorHandler.handleError(error)
        }
        OptionT.liftF(response)
      }

      case authReq@POST -> Root / "verifyEmail" as user => {
        val response: IO[Response[IO]] = authReq.req.decode[OTP] { request =>
          val action = for {
            _ <- repos.otp().exists(request.otp, user.id)
            user <- userService.getUser(user.id)
            updateResult <- userRolesService.makeUserTrial(user)
          } yield updateResult
          action.value.flatMap {
            case Right(tokens) => Ok(tokens.into[UserDTO].withFieldComputed(_.id, u => u._id.toHexString).transform)
            case Left(error) => httpErrorHandler.handleError(error)
          }
        }
        OptionT.liftF(response)
      }

      case _@POST -> Root / "logout" as user => {
        val response: IO[Response[IO]] = {
          val action = for {
            updateResult <- userService.logoutUser(user.id)
          } yield updateResult
          action.value.flatMap {
            case Right(_) => Ok()
            case Left(error) => httpErrorHandler.handleError(error)
          }
        }
        OptionT.liftF(response)
      }

      case authReq@POST -> Root / "feedbacks" as user => {
        val response: IO[Response[IO]] = authReq.req.decode[FeedbackRequest] { request =>
          val action = for {
            createResult <- userService.createFeedback(Feedback(request.topic, request.message, user.id))
          } yield createResult
          action.value.flatMap {
            case Right(_) => Ok()
            case Left(error) => httpErrorHandler.handleError(error)
          }
        }
        OptionT.liftF(response)
      }

      case _@GET -> Root / "purchase" / "android" as user => {
        val response: IO[Response[IO]] = {
          val action = for {
            token <- repos.androidPurchaseRepo().getLastForUser(user.id)
            purchase <- subscriptionsService.getAndroidPurchase(token.subscriptionId, token.purchaseToken)
            userO <- userService.getUser(user.id)
            premiumUser <- userRolesService.updateSubscribedUserRole(userO, purchase.startTimeMillis, purchase.expiryTimeMillis)
          } yield premiumUser
          action.value.flatMap {
            case Right(premiumUser) => Ok(premiumUser.into[UserDTO].withFieldComputed(_.id, u => u._id.toHexString).transform)
            case Left(error) => httpErrorHandler.handleError(error)
          }
        }
        OptionT.liftF(response)
      }

      case authReq@POST -> Root / "purchase" / "android" as user => {
        val response: IO[Response[IO]] = authReq.req.decode[AndroidReceiptValidationRequest] { request =>
          val action = for {
            _ <- subscriptionsService.getAndroidPurchase(request)
            savedToken <- repos.androidPurchaseRepo().create(AndroidToken(user.id, request.productId, request.token, System.currentTimeMillis()))
          } yield savedToken
          action.value.flatMap {
            case Right(_) => Ok()
            case Left(error) => httpErrorHandler.handleError(error)
          }
        }
        OptionT.liftF(response)
      }

      case _@GET -> Root / "purchase" / "apple" as user => {
        val response: IO[Response[IO]] = {
          val action = for {
            token <- repos.applePurchaseRepo().getLastForUser(user.id)
            purchase <- subscriptionsService.getApplePurchase(token.purchaseToken, secrets.read.surfsUp.apple.appSecret)
            dbUser <- userService.getUser(user.id)
            premiumUser <- userRolesService.updateSubscribedUserRole(dbUser, purchase.purchase_date_ms, purchase.expires_date_ms)
          } yield premiumUser
          action.value.flatMap {
            case Right(premiumUser) => Ok(premiumUser.into[UserDTO].withFieldComputed(_.id, u => u._id.toHexString).transform)
            case Left(error) => httpErrorHandler.handleError(error)
          }
        }
        OptionT.liftF(response)
      }

      case authReq@POST -> Root / "purchase" / "apple" as user => {
        val response: IO[Response[IO]] = authReq.req.decode[ApplePurchaseToken] { req =>
          val action: EitherT[IO, SurfsUpError, AppleToken] = for {
            _ <- subscriptionsService.getApplePurchase(req.token, secrets.read.surfsUp.apple.appSecret)
            savedToken <- repos.applePurchaseRepo().create(AppleToken(user.id, req.token, System.currentTimeMillis()))
          } yield savedToken
          action.value.flatMap {
            case Right(x) => Ok()
            case Left(error) => httpErrorHandler.handleThrowable(new RuntimeException(error))
          }
        }
        OptionT.liftF(response)
      }
    }

  def facebookEndpoints(): HttpRoutes[IO] = {

    HttpRoutes.of[IO] {
      case req@POST -> Root => {
        val action = for {
          rr <- EitherT.liftF(req.as[FacebookRegisterRequest])
          tokens <- userService.registerFacebookUser(rr)
        } yield tokens
        action.value.flatMap {
          case Right(tokens) => Ok(tokens)
          case Left(error) => httpErrorHandler.handleError(error)
        }
      }

      case req@POST -> Root / "login" =>
        val action = for {
          credentials <- EitherT.liftF(req.as[FacebookLoginRequest])
          tokens <- userService.loginFacebookUser(credentials)
        } yield tokens
        action.value.flatMap {
          case Right(tokens) => Ok(tokens)
          case Left(error) => httpErrorHandler.handleError(error)
        }
    }
  }

  def appleEndpoints(): HttpRoutes[IO] = {
    HttpRoutes.of[IO] {
      case req@POST -> Root => {
        val action = for {
          rr <- EitherT.liftF(req.as[AppleRegisterRequest])
          tokens <- userService.registerAppleUser(rr)
        } yield tokens
        action.value.flatMap {
          case Right(tokens) => Ok(tokens)
          case Left(error) => httpErrorHandler.handleError(error)
        }
      }

      case req@POST -> Root / "login" =>
        val action = for {
          credentials <- EitherT.liftF(req.as[AppleLoginRequest])
          tokens <- userService.loginAppleUser(credentials)
        } yield tokens
        action.value.flatMap {
          case Right(tokens) => Ok(tokens)
          case Left(error) => httpErrorHandler.handleError(error)
        }
    }

  }

  private def asSubscription(response: String):EitherT[IO, SurfsUpError, SubscriptionNotificationWrapper] = {
    EitherT(IO.fromEither(
      parse(response).map(json => json.as[SubscriptionNotificationWrapper].left.map(x=>UnknownError(x.message)))))
  }
}
