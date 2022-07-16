package com.uptech.windalerts.core.user.sessions

import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import com.uptech.windalerts.core._
import com.uptech.windalerts.core.types.AccessTokenRequest
import com.uptech.windalerts.core.user._
import com.uptech.windalerts.core.user.sessions.UserSessions.UserSession.REFRESH_TOKEN_EXPIRY
import dev.profunktor.auth.jwt.JwtSecretKey
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import java.util.concurrent.TimeUnit

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


  def reset[F[_]: Sync](id: String, newDeviceToken: String)(implicit infrastructure: Infrastructure[F], FR: Raise[F, UserNotFoundError]): F[Tokens] = {
    for {
      _ <- infrastructure.userSessionsRepository.deleteForUserId(id)
      tokens <- generateNewTokens(id, newDeviceToken)
    } yield tokens
  }

  def generateNewTokens[F[_]: Sync](id: String, deviceToken: String)(implicit infrastructure: Infrastructure[F]): F[Tokens] = {
    val accessToken = createAccessToken(UserId(id))
    for {
      newRefreshToken <- infrastructure.userSessionsRepository.create(utils.generateRandomString(40), System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY, id, deviceToken)
      tokens = Tokens(accessToken.accessToken, newRefreshToken, accessToken.expiredAt)
    } yield tokens
  }


  def createAccessToken[F[_]](userId: UserId)(implicit infrastructure: Infrastructure[F]): AccessTokenWithExpiry = AccessTokenWithExpiry(JwtSecretKey(infrastructure.jwtKey), userId)

  def refresh[F[_]: Sync](accessTokenRequest: AccessTokenRequest)(implicit infrastructure: Infrastructure[F], FR: Raise[F, UserNotFoundError], RTNF: Raise[F, RefreshTokenNotFoundError], RTE: Raise[F, RefreshTokenExpiredError]): F[Tokens] = {
    for {
      oldRefreshToken <- infrastructure.userSessionsRepository.getByRefreshToken(accessTokenRequest.refreshToken)
      _ <- checkNotExpired(oldRefreshToken)
      _ <- infrastructure.userSessionsRepository.deleteForUserId(oldRefreshToken.userId)
      tokens <- generateNewTokens(oldRefreshToken.userId, oldRefreshToken.deviceToken)
    } yield tokens
  }

  private def checkNotExpired[F[_]](oldRefreshToken: UserSession)(implicit infrastructure: Infrastructure[F], RTNF: Raise[F, RefreshTokenExpiredError], A: Applicative[F]) =
    if (!oldRefreshToken.isExpired()) {
      A.pure(())
    } else {
      RTNF.raise(RefreshTokenExpiredError())
    }

  def updateDeviceToken[F[_]](id: String, deviceToken: String)(implicit infrastructure: Infrastructure[F], FR: Raise[F, UserNotFoundError]): F[Unit] = {
    infrastructure.userSessionsRepository.updateDeviceToken(id, deviceToken)
  }

  def deleteForUserId[F[_]](userId: String)(implicit infrastructure: Infrastructure[F]): F[Unit] =
    infrastructure.userSessionsRepository.deleteForUserId(userId)

}