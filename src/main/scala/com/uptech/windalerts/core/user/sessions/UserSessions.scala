package com.uptech.windalerts.core.user.sessions

import cats.Applicative
import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import com.uptech.windalerts.core._
import com.uptech.windalerts.core.credentials.UserCredentialService
import UserSessions._
import UserSessions.AccessTokenWithExpiry
import UserSessions.UserSession.REFRESH_TOKEN_EXPIRY
import com.uptech.windalerts.core.types.{AccessTokenRequest, LoginRequest}
import com.uptech.windalerts.core.user._
import dev.profunktor.auth.jwt.{JwtAuth, JwtSecretKey}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import java.util.concurrent.TimeUnit

class UserSessions[F[_] : Sync](jwtKey: String, userCredentialsService: UserCredentialService[F], userRepository: UserRepository[F], userSessionsRepository: UserSessionRepository[F])(implicit A:Applicative[F]) {

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

  private def generateNewTokens(id: String, deviceToken: String)(implicit FR: Raise[F, UserNotFoundError]): F[TokensWithUser] = {
    for {
      user <- userRepository.getByUserId(id)
      session <- generateNewTokens(user, deviceToken)
    } yield session
  }

  def generateNewTokens(user: UserT, deviceToken: String): F[TokensWithUser] = {
    val token = createToken(UserId(user.id))
    userSessionsRepository.create(utils.generateRandomString(40), System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY, user.id, deviceToken)
      .map(newRefreshToken => TokensWithUser(token.accessToken, newRefreshToken.refreshToken, token.expiredAt, user))
  }

  def createToken(userId: UserId): AccessTokenWithExpiry = AccessTokenWithExpiry(JwtSecretKey(jwtKey), userId)

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

  def updateDeviceToken(id: String, deviceToken: String)(implicit FR: Raise[F, UserNotFoundError]): F[Unit] = {
    userSessionsRepository.updateDeviceToken(id, deviceToken)
  }

  def logout(userId: String): F[Unit] =
    userSessionsRepository.deleteForUserId(userId)

}

object UserSessions {

  case class AccessTokenWithExpiry(accessToken: String, expiredAt: Long)

  object AccessTokenWithExpiry {
    val ACCESS_TOKEN_EXPIRY = 6L * 60L * 60L * 1000L

    def apply(key: JwtSecretKey, userId: UserId): AccessTokenWithExpiry = {
      val current = System.currentTimeMillis()
      val expiry = current / 1000 + TimeUnit.MILLISECONDS.toSeconds(ACCESS_TOKEN_EXPIRY)
      val claims = JwtClaim(
        expiration = Some(expiry),
        issuedAt = Some(current / 1000),
        issuer = Some("wind-alerts.com"),
        subject = Some(userId.id)
      )

      AccessTokenWithExpiry(Jwt.encode(claims, key.value, JwtAlgorithm.HS256), expiry)
    }
  }

  case class UserSession(id: String, refreshToken: String, expiry: Long, userId: String, deviceToken: String) {
    def isExpired() = System.currentTimeMillis() > expiry
  }

  object UserSession {
    val REFRESH_TOKEN_EXPIRY = 14L * 24L * 60L * 60L * 1000L
  }

}