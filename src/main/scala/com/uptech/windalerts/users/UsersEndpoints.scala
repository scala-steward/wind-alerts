package com.uptech.windalerts.users

import cats.data.{EitherT, OptionT}
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.Repos
import com.uptech.windalerts.domain._
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain.{AppleLoginRequest, AppleRegisterRequest, ChangePasswordRequest, FacebookLoginRequest, FacebookRegisterRequest, ResetPasswordRequest, _}
import io.circe.parser._
import io.scalaland.chimney.dsl._
import org.http4s.{AuthedRoutes, HttpRoutes, Response}

class UsersEndpoints(repos: Repos[IO],
                     userService: UserService[IO],
                     userRolesService: UserRolesService[IO],
                     subscriptionsService: SubscriptionsService[IO],
                     httpErrorHandler: HttpErrorHandler[IO],
                     auth: AuthenticationService[IO],
                     otpService: OTPService[IO])(implicit cs: ContextShift[IO]) extends http[IO](httpErrorHandler) {

  def openEndpoints(): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case _@GET -> Root / "ping" =>
        handle(() => EitherT.liftF(IO("pong")))

      case _@GET -> Root / "privacy-policy" =>
        handle(() => EitherT.liftF(IO(statics.privacyPolicy)))

      case _@GET -> Root / "about-surfs-up" =>
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
        handle(resetPasswordRequest, (x: ResetPasswordRequest) => {
          userService.resetPassword(x.email, x.deviceType).map(_ => ())
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
        handleOk(authReq, user, (u: UserId, request: UpdateUserRequest) =>
          userService.updateUserProfile(u.id, request.name, request.snoozeTill, request.disableAllAlerts, request.notificationsPerHour)
            .map(tokens => tokens.into[UserDTO].withFieldComputed(_.id, u => u._id.toHexString).transform)
        )
      }

      case _@POST -> Root / "sendOTP" as user => {
        handleOkNoContentNoDecode(user, (u: UserId) =>
          for {
            userFromDb <- userService.getUser(user.id)
            sent <- otpService.send(userFromDb._id.toHexString, userFromDb.email)
          } yield sent
        )
      }

      case authReq@POST -> Root / "verifyEmail" as user => {
        handleOk(authReq, user, (u: UserId, request: OTP) =>
          for {
            _ <- repos.otp().exists(request.otp, user.id)
            user <- userService.getUser(user.id)
            updateResult <- userRolesService.makeUserTrial(user).map(tokens => tokens.into[UserDTO].withFieldComputed(_.id, u => u._id.toHexString).transform)
          } yield updateResult
        )
      }

      case _@POST -> Root / "logout" as user => {
        handleOkNoContentNoDecode(user, (u: UserId) => userService.logoutUser(user.id))
      }

      case authReq@POST -> Root / "feedbacks" as user => {
        handleEmptyOk(authReq, user, (u: UserId, request: FeedbackRequest) =>
          userService.createFeedback(Feedback(request.topic, request.message, user.id))
        )
      }

      case _@GET -> Root / "purchase" / "android" as user => {
        handleOkNoDecode(user, (u: UserId) => {
          for {
            token <- repos.androidPurchaseRepo().getLastForUser(u.id)
            purchase <- subscriptionsService.getAndroidPurchase(token.subscriptionId, token.purchaseToken)
            userO <- userService.getUser(u.id)
            premiumUser <- userRolesService.updateSubscribedUserRole(userO, purchase.startTimeMillis, purchase.expiryTimeMillis).map(premiumUser => premiumUser.into[UserDTO].withFieldComputed(_.id, u => u._id.toHexString).transform)
          } yield premiumUser
        }
        )
      }

      case authReq@POST -> Root / "purchase" / "android" as user => {
        handleEmptyOk(authReq, user, (u: UserId, request: AndroidReceiptValidationRequest) =>
          for {
            _ <- subscriptionsService.getAndroidPurchase(request)
            savedToken <- repos.androidPurchaseRepo().create(AndroidToken(user.id, request.productId, request.token, System.currentTimeMillis()))
          } yield savedToken
        )
      }

      case _@GET -> Root / "purchase" / "apple" as user => {
        handleOkNoDecode(user, (u: UserId) => {
          for {
            token <- repos.applePurchaseRepo().getLastForUser(user.id)
            purchase <- subscriptionsService.getApplePurchase(token.purchaseToken, secrets.read.surfsUp.apple.appSecret)
            dbUser <- userService.getUser(user.id)
            premiumUser <- userRolesService.updateSubscribedUserRole(dbUser, purchase.purchase_date_ms, purchase.expires_date_ms).map(premiumUser => premiumUser.into[UserDTO].withFieldComputed(_.id, u => u._id.toHexString).transform)
          } yield premiumUser
        }
        )
      }

      case authReq@POST -> Root / "purchase" / "apple" as user => {
        handleEmptyOk(authReq, user, (u: UserId, req: ApplePurchaseToken) =>
          for {
            _ <- subscriptionsService.getApplePurchase(req.token, secrets.read.surfsUp.apple.appSecret)
            savedToken <- repos.applePurchaseRepo().create(AppleToken(user.id, req.token, System.currentTimeMillis()))
          } yield savedToken
        )
      }
    }

  def facebookEndpoints(): HttpRoutes[IO] = {

    HttpRoutes.of[IO] {
      case req@POST -> Root => {
        val facebookRegisterRequest = req.as[FacebookRegisterRequest]
        handle(facebookRegisterRequest, userService.registerFacebookUser(_))
      }

      case req@POST -> Root / "login" =>
        val facebookLoginRequest = req.as[FacebookLoginRequest]
        handle(facebookLoginRequest, userService.loginFacebookUser(_))
    }
  }

  def appleEndpoints(): HttpRoutes[IO] = {
    HttpRoutes.of[IO] {
      case req@POST -> Root =>
        val appleRegisterRequest = req.as[AppleRegisterRequest]
        handle(appleRegisterRequest, userService.registerAppleUser(_))

      case req@POST -> Root / "login" =>
        val appleLoginRequest = req.as[AppleLoginRequest]
        handle(appleLoginRequest, userService.loginAppleUser(_))
    }

  }

  private def asSubscription(response: String): EitherT[IO, SurfsUpError, SubscriptionNotificationWrapper] = {
    EitherT(IO.fromEither(
      parse(response).map(json => json.as[SubscriptionNotificationWrapper].left.map(x => UnknownError(x.message)))))
  }
}
