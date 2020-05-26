package com.uptech.windalerts.users

import cats.data.{EitherT, OptionT}
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.Repos
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.{HttpErrorHandler, PrivacyPolicy, secrets}
import io.circe.parser._
import io.scalaland.chimney.dsl._
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, HttpRoutes, Response}
import org.log4s.getLogger

class UsersEndpoints(rpos: Repos[IO], userService: UserService[IO],
                     httpErrorHandler: HttpErrorHandler[IO],
                     auth: Auth)(implicit cs: ContextShift[IO])  extends Http4sDsl[IO] {
  private val logger = getLogger

  def openEndpoints(): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case _@GET -> Root / "ping"  =>
        val action: EitherT[IO, String, String] = for {
          response <- EitherT.liftF(IO("pong"))
        } yield response
        action.value.flatMap {
          case Right(x) => Ok(x)
          case Left(error) => httpErrorHandler.handleThrowable(new RuntimeException(error))
        }

      case _@GET -> Root / "privacy-policy"  =>
        val action: EitherT[IO, String, String] = for {
          response <- EitherT.liftF(IO(PrivacyPolicy.read))
        } yield response
        action.value.flatMap {
          case Right(x) => Ok(x)
          case Left(error) => httpErrorHandler.handleThrowable(new RuntimeException(error))
        }

      case req@POST -> Root =>
        val action = for {
          registerRequest <- EitherT.liftF(req.as[RegisterRequest])
          createUserResponse <- userService.createUser(registerRequest)
          emailConf <- EitherT.liftF(IO(secrets.read.surfsUp.email))
          emailSender <- EitherT.liftF(IO(new EmailSender(emailConf.userName, emailConf.password)))

          otp <- createOTP
          _ <- EitherT.liftF(rpos.otp().create(OTPWithExpiry(otp, System.currentTimeMillis() + 5 * 60 * 1000, createUserResponse._id.toHexString)))
          _ <- EitherT.liftF(IO(emailSender.sendOtp(createUserResponse.email, otp)))
          dbCredentials <- EitherT.right(IO(new Credentials(createUserResponse._id, registerRequest.email, registerRequest.password, registerRequest.deviceType)))
          accessTokenId <- EitherT.right(IO(auth.generateRandomString(10)))
          token <- auth.createToken(dbCredentials._id.toHexString, accessTokenId)
          refreshToken <- EitherT.liftF(rpos.refreshTokenRepo().create(RefreshToken(auth.generateRandomString(40), (System.currentTimeMillis() + auth.REFRESH_TOKEN_EXPIRY), dbCredentials._id.toHexString, accessTokenId)))
          tokens <- auth.tokens(token.accessToken, refreshToken, token.expiredAt, createUserResponse)
        } yield tokens
        action.value.flatMap {
          case Right(tokens) => Ok(tokens)
          case Left(error) => httpErrorHandler.handleError(error)
        }

      case req@POST -> Root / "login" =>
        val action = for {
          credentials <- EitherT.liftF(req.as[LoginRequest])
          dbCredentials <- userService.getByCredentials(credentials.email, credentials.password, credentials.deviceType)
          dbUser <- userService.getUser(dbCredentials.email, dbCredentials.deviceType)
          _ <- userService.updateDeviceToken(dbCredentials._id.toHexString, credentials.deviceToken).toRight(CouldNotUpdateUserDeviceError())
          accessTokenId <- EitherT.right(IO(auth.generateRandomString(10)))
          token <- auth.createToken(dbCredentials._id.toHexString, accessTokenId)
          _ <- EitherT.liftF(rpos.refreshTokenRepo().deleteForUserId(dbCredentials._id.toHexString))
          refreshToken <- EitherT.liftF(rpos.refreshTokenRepo().create(RefreshToken(auth.generateRandomString(40), (System.currentTimeMillis() + auth.REFRESH_TOKEN_EXPIRY), dbCredentials._id.toHexString, accessTokenId)))
          tokens <- auth.tokens(token.accessToken, refreshToken, token.expiredAt, dbUser)
        } yield tokens
        action.value.flatMap {
          case Right(tokens) => Ok(tokens)
          case Left(error) => httpErrorHandler.handleError(error)
        }

      case req@POST -> Root / "refresh" =>
        val action = for {
          refreshToken <- EitherT.liftF(req.as[AccessTokenRequest])
          oldRefreshToken <- rpos.refreshTokenRepo().getByRefreshToken(refreshToken.refreshToken).toRight(RefreshTokenNotFoundError())
          oldValidRefreshToken <- {
            val eitherT: EitherT[IO, RefreshTokenExpiredError, RefreshToken] = EitherT.fromEither {
              if (oldRefreshToken.isExpired()) {
                Left(RefreshTokenExpiredError())
              } else {
                Right(oldRefreshToken)
              }
            }
            eitherT
          }
          accessTokenId <- EitherT.right(IO(auth.generateRandomString(10)))
          token <- auth.createToken(oldValidRefreshToken.userId, accessTokenId)
          _ <- EitherT.liftF(rpos.refreshTokenRepo().deleteForUserId(oldValidRefreshToken.userId))
          newRefreshToken <- EitherT.liftF(rpos.refreshTokenRepo().create(RefreshToken(auth.generateRandomString(40), (System.currentTimeMillis() + auth.REFRESH_TOKEN_EXPIRY), oldValidRefreshToken.userId, accessTokenId)))
          user <- userService.getUser(newRefreshToken.userId)

          tokens <- auth.tokens(token.accessToken, newRefreshToken, token.expiredAt, user)
        } yield tokens
        action.value.flatMap {
          case Right(tokens) => Ok(tokens)
          case Left(error) => httpErrorHandler.handleError(error)
        }

      case req@POST -> Root / "changePassword" =>
        val action = for {
          request <- EitherT.liftF(req.as[ChangePasswordRequest])
          dbCredentials <- userService.getByCredentials(request.email, request.oldPassword, request.deviceType)
          _ <- userService.updatePassword(dbCredentials._id.toHexString, request.newPassword).toRight(CouldNotUpdatePasswordError()).asInstanceOf[EitherT[IO, ValidationError, Unit]]
          _ <- EitherT.liftF(rpos.refreshTokenRepo().deleteForUserId(dbCredentials._id.toHexString)).asInstanceOf[EitherT[IO, ValidationError, Unit]]
        } yield ()
        action.value.flatMap {
          case Right(_) => Ok()
          case Left(error) => httpErrorHandler.handleError(error)
        }

      case req@POST -> Root / "resetPassword" =>
        val action = for {
          request <- EitherT.liftF(req.as[ResetPasswordRequest])
          dbUser <- userService.resetPassword(request.email, request.deviceType)
          _ <- EitherT.liftF(rpos.refreshTokenRepo().deleteForUserId(dbUser._id.toHexString)).asInstanceOf[EitherT[IO, ValidationError, Unit]]
        } yield ()
        action.value.flatMap {
          case Right(_) => Ok()
          case Left(error) => httpErrorHandler.handleError(error)
        }


      case req@POST -> Root / "purchase" / "android" / "update" => {


        val action: EitherT[IO, ValidationError, UserT] = for {
          _ <- EitherT.liftF(IO(logger.error(s"Called request ${req}")))
          _ <- EitherT.liftF(IO(logger.error(s"Called request ${req.body}")))

          update <- EitherT.liftF(req.as[AndroidUpdate])
          _ <- EitherT.liftF(IO(logger.error(s"Update received is ${update}")))
          response <- EitherT.liftF(
            IO(new String(java.util.Base64.getDecoder.decode(update.message.data))))
          _ <- EitherT.liftF(IO(logger.error(s"Decoded  is ${response}")))
          subscription <- asSubscription(response)
          _ <- EitherT.liftF(IO(logger.error(s"Decoded is ${response}")))
          token <- rpos.androidPurchaseRepo().getPurchaseByToken(subscription.subscriptionNotification.purchaseToken)
          _ <- EitherT.liftF(IO(logger.error(s"Token is ${token}")))
          purchase <- userService.getAndroidPurchase(token.subscriptionId, subscription.subscriptionNotification.purchaseToken)
          _ <- EitherT.liftF(IO(logger.error(s"Purchase is ${purchase}")))
          updatedUser <- userService.updateSubscribedUserRole(UserId(token.userId), purchase.startTimeMillis, purchase.expiryTimeMillis)
          _ <- EitherT.liftF(IO(logger.error(s"updatedUser is ${updatedUser}")))
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

      case authReq@POST -> Root / "sendOTP" as user => {
        val action = for {
          emailConf <- EitherT.liftF(IO(secrets.read.surfsUp.email))
          emailSender <- EitherT.liftF(IO(new EmailSender(emailConf.userName, emailConf.password)))
          userFromDb <- userService.getUser(user.id)
          otp <- createOTP
          updated <- otpWithExpiry(user, otp)

          sent <- send(emailSender, userFromDb, otp)

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
            updateResult <- rpos.otp().exists(request.otp, user.id)
            updateResult <- userService.verifyEmail(user.id)
          } yield updateResult
          action.value.flatMap {
            case Right(tokens) => Ok(tokens.into[UserDTO].withFieldComputed(_.id, u => u._id.toHexString).transform)
            case Left(error) => httpErrorHandler.handleError(error)
          }
        }
        OptionT.liftF(response)
      }

      case authReq@POST -> Root / "logout" as user => {
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
            token <- rpos.androidPurchaseRepo().getLastForUser(user.id)
            purchase <- userService.getAndroidPurchase(token.subscriptionId, token.purchaseToken)
            premiumUser <- userService.updateSubscribedUserRole(user, purchase.startTimeMillis, purchase.expiryTimeMillis)
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
            purchase <- userService.getAndroidPurchase(request)
            savedToken <- rpos.androidPurchaseRepo().create(AndroidToken(user.id, request.productId, request.token, System.currentTimeMillis()))
          } yield savedToken
          action.value.flatMap {
            case Right(_) => Ok()
            case Left(error) => httpErrorHandler.handleError(error)
          }
        }
        OptionT.liftF(response)
      }

      case authReq@GET -> Root / "purchase" / "apple" as user => {
        val response: IO[Response[IO]] = {
          val action = for {
            token <- rpos.applePurchaseRepo().getLastForUser(user.id)
            purchase <- userService.getApplePurchase(token.purchaseToken, secrets.read.surfsUp.apple.appSecret)
            premiumUser <- userService.updateSubscribedUserRole(user, purchase.purchase_date_ms, purchase.expires_date_ms)
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
          val action: EitherT[IO, ValidationError, AppleToken] = for {
            response <- userService.getApplePurchase(req.token, secrets.read.surfsUp.apple.appSecret)
            savedToken <- rpos.applePurchaseRepo().create(AppleToken(user.id, req.token, System.currentTimeMillis()))
          } yield savedToken
          action.value.flatMap {
            case Right(x) => Ok()
            case Left(error) => httpErrorHandler.handleThrowable(new RuntimeException(error))
          }
        }
        OptionT.liftF(response)
      }
    }

  private def send(emailSender: EmailSender, userFromDb: UserT, otp: String): EitherT[IO, ValidationError, String] = {
    EitherT.liftF(IO({
      emailSender.sendOtp(userFromDb.email, otp)
      otp
    }))
  }

  private def otpWithExpiry(user: UserId, otp: String): EitherT[IO, ValidationError, OTPWithExpiry] = {
    EitherT.liftF(rpos.otp().updateForUser(user.id, OTPWithExpiry(otp, System.currentTimeMillis() + 5 * 60 * 1000, user.id)))
  }

  private def createOTP: EitherT[IO, ValidationError, String] = {
    EitherT.liftF(IO(auth.createOtp(4)))
  }

  def facebookEndpoints(): HttpRoutes[IO] = {
    HttpRoutes.of[IO] {
      case req@POST -> Root => {
        val action = for {
          rr <- EitherT.liftF(req.as[FacebookRegisterRequest])
          result <- userService.createUser(rr)
          accessTokenId <- EitherT.right(IO(auth.generateRandomString(10)))
          token <- auth.createToken(result._2._id.toHexString, accessTokenId)
          refreshToken <- EitherT.liftF(
            rpos.refreshTokenRepo().create(RefreshToken(auth.generateRandomString(40),
              System.currentTimeMillis() + auth.REFRESH_TOKEN_EXPIRY,
              result._2._id.toHexString, accessTokenId)))
          tokens <- auth.tokens(token.accessToken, refreshToken, token.expiredAt, result._1)
        } yield tokens
        action.value.flatMap {
          case Right(tokens) => Ok(tokens)
          case Left(error) => httpErrorHandler.handleError(error)
        }
      }

      case req@POST -> Root / "login" =>
        val action = for {
          credentials <- EitherT.liftF(req.as[FacebookLoginRequest])
          dbUser <- userService.getFacebookUserByAccessToken(credentials.accessToken, credentials.deviceType)
          _ <- userService.updateDeviceToken(dbUser._id.toHexString, credentials.deviceToken).toRight(CouldNotUpdateUserDeviceError())
          accessTokenId <- EitherT.right(IO(auth.generateRandomString(10)))
          token <- auth.createToken(dbUser._id.toHexString, accessTokenId)
          _ <- EitherT.liftF(rpos.refreshTokenRepo().deleteForUserId(dbUser._id.toHexString))
          refreshToken <- EitherT.liftF(rpos.refreshTokenRepo().create(RefreshToken(auth.generateRandomString(40), (System.currentTimeMillis() + auth.REFRESH_TOKEN_EXPIRY), dbUser._id.toHexString, accessTokenId)))
          tokens <- auth.tokens(token.accessToken, refreshToken, token.expiredAt, dbUser)
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
          result <- userService.createUser(rr)
          accessTokenId <- EitherT.right(IO(auth.generateRandomString(10)))
          token <- auth.createToken(result._2._id.toHexString, accessTokenId)
          refreshToken <- EitherT.liftF(
            rpos.refreshTokenRepo().create(RefreshToken(auth.generateRandomString(40),
              System.currentTimeMillis() + auth.REFRESH_TOKEN_EXPIRY,
              result._2._id.toHexString, accessTokenId)))
          tokens <- auth.tokens(token.accessToken, refreshToken, token.expiredAt, result._1)
        } yield tokens
        action.value.flatMap {
          case Right(tokens) => Ok(tokens)
          case Left(error) => httpErrorHandler.handleError(error)
        }
      }

      case req@POST -> Root / "login" =>
        val action = for {
          credentials <- EitherT.liftF(req.as[AppleLoginRequest])
          dbUser <- userService.loginUser(credentials)
          updateDevice <- userService.updateDeviceToken(dbUser._id.toHexString, credentials.deviceToken).toRight(CouldNotUpdateUserDeviceError())
          accessTokenId <- EitherT.right(IO(auth.generateRandomString(10)))
          token <- auth.createToken(dbUser._id.toHexString, accessTokenId)
          _ <- EitherT.liftF(rpos.refreshTokenRepo().deleteForUserId(dbUser._id.toHexString))
          refreshToken <- EitherT.liftF(rpos.refreshTokenRepo().create(RefreshToken(auth.generateRandomString(40), (System.currentTimeMillis() + auth.REFRESH_TOKEN_EXPIRY), dbUser._id.toHexString, accessTokenId)))
          tokens <- auth.tokens(token.accessToken, refreshToken, token.expiredAt, dbUser)
        } yield tokens
        action.value.flatMap {
          case Right(tokens) => Ok(tokens)
          case Left(error) => httpErrorHandler.handleError(error)
        }
    }
  }



  private def asSubscription(response: String):EitherT[IO, ValidationError, SubscriptionNotificationWrapper] = {
    EitherT(IO.fromEither(
      parse(response).map(json => json.as[SubscriptionNotificationWrapper].left.map(x=>UnknownError(x.message)))))
  }
}
