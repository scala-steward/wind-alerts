package com.uptech.windalerts.core.user

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.core.credentials.{Credentials, UserCredentialService}
import com.uptech.windalerts.core.otp.OTPService
import com.uptech.windalerts.core.refresh.tokens.{RefreshToken, RefreshTokenRepository}
import com.uptech.windalerts.core.{RefreshTokenExpiredError, RefreshTokenNotFoundError, SurfsUpError, UserAlreadyExistsError, UserNotFoundError, utils}
import com.uptech.windalerts.config._
import com.uptech.windalerts.infrastructure.endpoints.dtos._
import com.uptech.windalerts.infrastructure.repositories.mongo.Repos
import org.mongodb.scala.bson.ObjectId

class UserService[F[_] : Sync](userRepository: UserRepository[F],
                               userCredentialsService: UserCredentialService[F],
                               otpService: OTPService[F],
                               auth: AuthenticationService[F],
                               refreshTokenRepository: RefreshTokenRepository[F]) {

  def register(registerRequest: RegisterRequest): EitherT[F, UserAlreadyExistsError, TokensWithUser] = {
    for {
      createUserResponse <- create(registerRequest)
      tokens <- EitherT.right(generateNewTokens(createUserResponse._1))
      _ <- EitherT.right(otpService.send(createUserResponse._1._id.toHexString, createUserResponse._1.email))
    } yield tokens
  }

  def create(rr: RegisterRequest): EitherT[F, UserAlreadyExistsError, (UserT, Credentials)] = {
    for {
      savedCreds <- userCredentialsService.createIfDoesNotExist(rr)
      saved <- EitherT.right(userRepository.create(
        UserT.createEmailUser(new ObjectId(savedCreds._id.toHexString),
          rr.email,
          rr.name,
          rr.deviceToken,
          rr.deviceType)))
    } yield (saved, savedCreds)
  }

  def login(credentials: LoginRequest): EitherT[F, SurfsUpError, TokensWithUser] = {
    for {
      dbCredentials <- userCredentialsService.getByCredentials(credentials.email, credentials.password, credentials.deviceType)
      dbUser <- getUser(dbCredentials.email, dbCredentials.deviceType)
      tokens <- resetUserSession(dbUser, credentials.deviceToken).leftWiden[SurfsUpError]
    } yield tokens
  }

  def resetUserSession(dbUser: UserT, newDeviceToken: String):EitherT[F, UserNotFoundError, TokensWithUser] = {
    for {
      _ <- updateDeviceToken(dbUser._id.toHexString, newDeviceToken)
      _ <- EitherT.liftF(refreshTokenRepository.deleteForUserId(dbUser._id.toHexString))
      tokens <- EitherT.right(generateNewTokens(dbUser.copy(deviceToken = newDeviceToken)))
    } yield tokens
  }

  def refresh(refreshToken: AccessTokenRequest): EitherT[F, SurfsUpError, TokensWithUser] = {
    for {
      oldRefreshToken <- refreshTokenRepository.getByRefreshToken(refreshToken.refreshToken).toRight(RefreshTokenNotFoundError())
      oldValidRefreshToken <- updateIfNotExpired(oldRefreshToken)
      _ <- EitherT.liftF(refreshTokenRepository.deleteForUserId(oldValidRefreshToken.userId))
      user <- getUser(oldRefreshToken.userId)
      tokens <- EitherT.right[SurfsUpError](generateNewTokens(user))
    } yield tokens
  }

  private def updateIfNotExpired(oldRefreshToken: RefreshToken): cats.data.EitherT[F, SurfsUpError, RefreshToken] = {
    if (oldRefreshToken.isExpired()) {
      EitherT.fromEither(Left(RefreshTokenExpiredError()))
    } else {
      import cats.implicits._
      refreshTokenRepository.updateExpiry(oldRefreshToken._id.toHexString, (System.currentTimeMillis() + RefreshToken.REFRESH_TOKEN_EXPIRY)).leftWiden[SurfsUpError]
    }
  }

  def generateNewTokens(user: UserT): F[TokensWithUser] = {
    import cats.syntax.functor._

    val accessTokenId = utils.generateRandomString(10)
    val token = auth.createToken(user._id.toHexString, accessTokenId)

    refreshTokenRepository.create(RefreshToken(user._id.toHexString, accessTokenId))
      .map(newRefreshToken=>auth.tokens(token.accessToken, newRefreshToken, token.expiredAt, user))
  }

  def updateUserProfile(id: String, name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long): EitherT[F, UserNotFoundError, UserT] = {
    for {
      user <- getUser(id)
      operationResult <- updateUser(name, snoozeTill, disableAllAlerts, notificationsPerHour, user).toRight(UserNotFoundError())
    } yield operationResult
  }

  private def updateUser(name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, user: UserT) = {
    userRepository.update(user.copy(name = name, snoozeTill = snoozeTill, disableAllAlerts = disableAllAlerts, notificationsPerHour = notificationsPerHour))
  }

  def updateDeviceToken(id: String, deviceToken: String): EitherT[F, UserNotFoundError, UserT] = {
    for {
      user <- getUser(id)
      operationResult <- userRepository.update(user.copy(deviceToken = deviceToken)).toRight(UserNotFoundError())
    } yield operationResult
  }


  def logoutUser(userId: String): EitherT[F, UserNotFoundError, Unit] = {
    for {
      _ <- EitherT.liftF(refreshTokenRepository.deleteForUserId(userId))
      _ <- updateDeviceToken(userId, "")
    } yield ()
  }

  def getUser(email: String, deviceType: String): EitherT[F, UserNotFoundError, UserT] =
    userRepository.getByEmailAndDeviceType(email, deviceType).toRight(UserNotFoundError())

  def getUser(userId: String): EitherT[F, UserNotFoundError, UserT] =
    userRepository.getByUserId(userId).toRight(UserNotFoundError())

  def sendOtp(userId: String): EitherT[F, UserNotFoundError, Unit] = {
    for {
      userFromDb <- getUser(userId)
      sent <- EitherT.right(otpService.send(userFromDb._id.toHexString, userFromDb.email))
    } yield sent
  }

}

object UserService {
  def apply[F[_] : Sync](
                          userRepository: UserRepository[F],
                          userCredentialService: UserCredentialService[F],
                          otpService: OTPService[F],
                          authenticationService: AuthenticationService[F],
                          refreshTokenRepo : RefreshTokenRepository[F]
                        ): UserService[F] =
    new UserService(userRepository, userCredentialService, otpService, authenticationService, refreshTokenRepo)
}