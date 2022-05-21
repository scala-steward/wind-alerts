package com.uptech.windalerts.core.refresh.tokens

import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import com.uptech.windalerts.core._
import com.uptech.windalerts.core.credentials.UserCredentialService
import com.uptech.windalerts.core.refresh.tokens.UserSession.REFRESH_TOKEN_EXPIRY
import com.uptech.windalerts.core.types.{AccessTokenRequest, LoginRequest}
import com.uptech.windalerts.core.user.{AuthenticationService, TokensWithUser, UserId, UserRepository, UserT}

class UserSessions[F[_] : Sync](auth: AuthenticationService[F], userCredentialsService: UserCredentialService[F], userRepository: UserRepository[F], userSessionsRepository: UserSessionRepository[F]) {

  def login(credentials: LoginRequest)(implicit FR: Raise[F, UserNotFoundError], UAF: Raise[F, UserAuthenticationFailedError]): F[TokensWithUser] = {
    for {
      persistedCredentials <- userCredentialsService.findByEmailAndPassword(credentials.email, credentials.password, credentials.deviceType)
      tokens <- reset(persistedCredentials.id, credentials.deviceToken)
    } yield tokens
  }

  def reset(id: String, newDeviceToken: String)(implicit FR: Raise[F, UserNotFoundError]): F[TokensWithUser] = {
    for {
      _ <- userSessionsRepository.deleteForUserId(id)
      tokens <- generateNewTokens(id, newDeviceToken)
    } yield tokens
  }

  def generateNewTokens(id: String, deviceToken: String)(implicit FR: Raise[F, UserNotFoundError]): F[TokensWithUser] = {
    for {
      user <- userRepository.getByUserId(id)
      session <- generateNewTokens(user, deviceToken)
    } yield session
  }

  def generateNewTokens(user: UserT, deviceToken: String): F[TokensWithUser] = {
    val token = auth.createToken(UserId(user.id))
    userSessionsRepository.create(utils.generateRandomString(40), System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY, user.id, deviceToken)
      .map(newRefreshToken => TokensWithUser(token.accessToken, newRefreshToken.refreshToken, token.expiredAt, user))
  }

  def refresh(accessTokenRequest: AccessTokenRequest)(implicit FR: Raise[F, UserNotFoundError], RTNF: Raise[F, RefreshTokenNotFoundError], RTE: Raise[F, RefreshTokenExpiredError]): F[TokensWithUser] = {
    for {
      oldRefreshToken <- userSessionsRepository.getByRefreshToken(accessTokenRequest.refreshToken)
      _ <- checkNotExpired(oldRefreshToken)
      _ <- userSessionsRepository.deleteForUserId(oldRefreshToken.userId)
      tokens <- generateNewTokens(oldRefreshToken.userId, oldRefreshToken.deviceToken)
    } yield tokens
  }

  private def checkNotExpired(oldRefreshToken: UserSession)(implicit RTNF: Raise[F, RefreshTokenExpiredError], A: Applicative[F]) =
    if (!oldRefreshToken.isExpired()) {
      A.pure(())
    } else {
      RTNF.raise(RefreshTokenExpiredError())
    }

  def updateDeviceToken(id: String, deviceToken: String)(implicit FR: Raise[F, UserNotFoundError]): F[UserT] = {
    for {
      _ <- userSessionsRepository.updateDeviceToken(id, deviceToken)
      user <- userRepository.getByUserId(id)
    } yield user
  }

  def logout(userId: String): F[Unit] =
    userSessionsRepository.deleteForUserId(userId)

}
