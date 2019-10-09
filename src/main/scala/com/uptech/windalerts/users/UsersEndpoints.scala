package com.uptech.windalerts.users

import cats.data.{EitherT, OptionT}
import cats.effect.IO
import com.uptech.windalerts.domain.{HttpErrorHandler, domain}
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain.{AccessTokenRequest, AlertRequest, ChangePasswordRequest, Credentials, LoginRequest, RefreshToken, RegisterRequest, UserId}
import org.http4s.{AuthedRoutes, HttpRoutes}
import org.http4s.dsl.Http4sDsl

class UsersEndpoints(userService: UserService,
                     httpErrorHandler: HttpErrorHandler[IO],
                     refreshTokenRepositoryAlgebra: RefreshTokenRepositoryAlgebra,
                     auth: Auth) extends Http4sDsl[IO] {


  def openEndpoints(): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case req@POST -> Root  =>
        val action = for {
          rr <- EitherT.liftF(req.as[RegisterRequest])
          result <- userService.createUser(rr)
          dbCredentials <- EitherT.right(IO(Credentials(Some(result.id), rr.email, rr.password, rr.deviceType)))
          accessTokenId <- EitherT.right(IO(auth.generateRandomString(10)))
          token <- auth.createToken(dbCredentials.id.get, 60, accessTokenId)
          refreshToken <- EitherT.liftF(refreshTokenRepositoryAlgebra.create(RefreshToken(auth.generateRandomString(40), (System.currentTimeMillis() + auth.REFRESH_TOKEN_EXPIRY), dbCredentials.id.get, accessTokenId)))
          tokens <- auth.tokens(token.accessToken, refreshToken, token.expiredAt, result)
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
    }


}
