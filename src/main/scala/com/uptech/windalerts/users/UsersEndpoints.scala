package com.uptech.windalerts.users

import cats.data.{EitherT, OptionT}
import cats.effect.IO
import com.softwaremill.sttp.{HttpURLConnectionBackend, sttp, _}
import com.uptech.windalerts.domain.{HttpErrorHandler, config, secrets}
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain._
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, HttpRoutes, Response}
import org.log4s.getLogger
import cats.Applicative
import cats.effect.{IO, Sync}
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.BeachId
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json, parser}
import org.http4s
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

import scala.runtime.Nothing$

class UsersEndpoints(userService: UserService,
                     httpErrorHandler: HttpErrorHandler[IO],
                     refreshTokenRepositoryAlgebra: RefreshTokenRepositoryAlgebra,
                     otpRepository: OtpRepository,
                     auth: Auth) extends Http4sDsl[IO] {
  private val logger = getLogger

  def authedService(): AuthedRoutes[UserId, IO] =
    AuthedRoutes {
      case authReq@PUT -> Root / "profile" as user => {
        val response: IO[Response[IO]] = authReq.req.decode[UpdateUserRequest] { request =>
          val action = for {
            updateResult <- userService.updateUserProfile(user.id, request.name, request.snoozeTill, request.notificationsPerHour)
          } yield updateResult
          action.value.flatMap {
            case Right(tokens) => Ok(tokens)
            case Left(error) => httpErrorHandler.handleError(error)
          }
        }
        OptionT.liftF(response)
      }

      case authReq@POST -> Root / "verifyEmail" as user => {
        val response: IO[Response[IO]] = authReq.req.decode[OTP] { request =>
          val action = for {
            updateResult <- otpRepository.exists(request.otp, user.id)
            updateResult <- userService.verifyEmail(user.id)
          } yield updateResult
          action.value.flatMap {
            case Right(tokens) => Ok(tokens)
            case Left(error) => httpErrorHandler.handleError(error)
          }
        }
        OptionT.liftF(response)
      }

    }


  def socialEndpoints(): HttpRoutes[IO] = {
    HttpRoutes.of[IO] {

      case req@POST -> Root => {
        val action = for {
          rr <- EitherT.liftF(req.as[FacebookRegisterRequest])
          result <- userService.createUser(rr)
          accessTokenId <- EitherT.right(IO(auth.generateRandomString(10)))
          token <- auth.createToken(result._2.id.get, 60, accessTokenId)
          refreshToken <- EitherT.liftF(refreshTokenRepositoryAlgebra.create(RefreshToken(auth.generateRandomString(40), (System.currentTimeMillis() + auth.REFRESH_TOKEN_EXPIRY), result._2.id.get, accessTokenId)))
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
          updateDevice <- userService.updateDeviceToken(dbUser.id, credentials.deviceToken).toRight(CouldNotUpdateUserDeviceError())
          accessTokenId <- EitherT.right(IO(auth.generateRandomString(10)))
          token <- auth.createToken(dbUser.id, 60, accessTokenId)
          deleteOldTokens <- EitherT.liftF(refreshTokenRepositoryAlgebra.deleteForUserId(dbUser.id))
          refreshToken <- EitherT.liftF(refreshTokenRepositoryAlgebra.create(RefreshToken(auth.generateRandomString(40), (System.currentTimeMillis() + auth.REFRESH_TOKEN_EXPIRY), dbUser.id, accessTokenId)))
          tokens <- auth.tokens(token.accessToken, refreshToken, token.expiredAt, dbUser)
        } yield tokens
        action.value.flatMap {
          case Right(tokens) => Ok(tokens)
          case Left(error) => httpErrorHandler.handleError(error)
        }
    }
  }

  def openEndpoints(): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      case req@POST -> Root =>
        val action = for {
          registerRequest <- EitherT.liftF(req.as[RegisterRequest])
          createUserResponse <- userService.createUser(registerRequest)
          emailConf <- EitherT.liftF(IO(secrets.read.surfsUp.email))
          emailSender <- EitherT.liftF(IO(new EmailSender(emailConf.userName, emailConf.password)))

          otp <- EitherT.liftF(IO(auth.createOtp(4)))
          _ <- EitherT.liftF(otpRepository.create(OTPWithExpiry(otp, System.currentTimeMillis() + 5 * 60 * 1000, createUserResponse.id)))
          _ <- EitherT.liftF(IO(emailSender.sendOtp(createUserResponse.email, otp)))
          dbCredentials <- EitherT.right(IO(Credentials(Some(createUserResponse.id), registerRequest.email, registerRequest.password, registerRequest.deviceType)))
          accessTokenId <- EitherT.right(IO(auth.generateRandomString(10)))
          token <- auth.createToken(dbCredentials.id.get, 60, accessTokenId)
          refreshToken <- EitherT.liftF(refreshTokenRepositoryAlgebra.create(RefreshToken(auth.generateRandomString(40), (System.currentTimeMillis() + auth.REFRESH_TOKEN_EXPIRY), dbCredentials.id.get, accessTokenId)))
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
          dbUser <- userService.getUserAndUpdateRole(dbCredentials.email, dbCredentials.deviceType)
          updateDevice <- userService.updateDeviceToken(dbCredentials.id.get, credentials.deviceToken).toRight(CouldNotUpdateUserDeviceError())
          accessTokenId <- EitherT.right(IO(auth.generateRandomString(10)))
          token <- auth.createToken(dbCredentials.id.get, 60, accessTokenId)
          deleteOldTokens <- EitherT.liftF(refreshTokenRepositoryAlgebra.deleteForUserId(dbCredentials.id.get))
          refreshToken <- EitherT.liftF(refreshTokenRepositoryAlgebra.create(RefreshToken(auth.generateRandomString(40), (System.currentTimeMillis() + auth.REFRESH_TOKEN_EXPIRY), dbCredentials.id.get, accessTokenId)))
          tokens <- auth.tokens(token.accessToken, refreshToken, token.expiredAt, dbUser)
        } yield tokens
        action.value.flatMap {
          case Right(tokens) => Ok(tokens)
          case Left(error) => httpErrorHandler.handleError(error)
        }

      case req@POST -> Root / "refresh" =>
        val action = for {
          refreshToken <- EitherT.liftF(req.as[AccessTokenRequest])
          oldRefreshToken <- refreshTokenRepositoryAlgebra.getByRefreshToken(refreshToken.refreshToken).toRight(RefreshTokenNotFoundError())
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
          token <- auth.createToken(oldValidRefreshToken.userId, 60, accessTokenId)
          _ <- EitherT.liftF(refreshTokenRepositoryAlgebra.deleteForUserId(oldValidRefreshToken.userId))
          newRefreshToken <- EitherT.liftF(refreshTokenRepositoryAlgebra.create(RefreshToken(auth.generateRandomString(40), (System.currentTimeMillis() + auth.REFRESH_TOKEN_EXPIRY), oldValidRefreshToken.userId, accessTokenId)))
          user <- userService.getUser(newRefreshToken.userId)
          dbUser <- userService.getUserAndUpdateRole(newRefreshToken.userId)

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
          _ <- userService.updatePassword(dbCredentials.id.get, request.newPassword).toRight(CouldNotUpdateUserDeviceError()).asInstanceOf[EitherT[IO, ValidationError, Unit]]
          _ <- EitherT.liftF(refreshTokenRepositoryAlgebra.deleteForUserId(dbCredentials.id.get)).asInstanceOf[EitherT[IO, ValidationError, Unit]]
        } yield ()
        action.value.flatMap {
          case Right(_) => Ok()
          case Left(error) => httpErrorHandler.handleError(error)
        }

      case req@POST -> Root / "purchase" / "apple" =>
        val action = for {
          reciptData <- EitherT.liftF(req.as[String])
          response <- verifyApple(reciptData, "password")
        } yield response
        action.value.flatMap {
          case Right(x) => Ok(x)
          case Left(error) => httpErrorHandler.handleError((OtpNotFoundError()))
        }


    }

  private def verifyApple(receiptData:String, password:String):EitherT[IO, String, String]  = {
    implicit val backend = HttpURLConnectionBackend()

    EitherT.fromEither(sttp.body(Map("receipt-data" -> "receiptData"))
      .post(uri"https://sandbox.itunes.apple.com/verifyReceipt")
      .send().body)
  }
}
