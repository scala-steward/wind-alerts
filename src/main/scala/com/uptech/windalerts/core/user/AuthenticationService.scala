package com.uptech.windalerts.core.user

import cats.data.EitherT
import cats.effect.Effect
import com.uptech.windalerts.Repos
import com.uptech.windalerts.core.refresh.tokens.RefreshToken
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.domain
import dev.profunktor.auth.JwtAuthMiddleware
import dev.profunktor.auth.jwt.{JwtAuth, JwtSecretKey}
import io.circe.parser._
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import java.util.concurrent.TimeUnit

case class AccessTokenWithExpiry(accessToken: String, expiredAt: Long)


class AuthenticationService[F[_] : Effect](repos: Repos[F]) {
  val ACCESS_TOKEN_EXPIRY = 1L * 24L * 60L * 60L * 1000L

  private val key = JwtSecretKey("secretKey")
  val jwtAuth = JwtAuth.hmac("secretKey", JwtAlgorithm.HS256)

  val authenticate: JwtClaim => F[Option[UserId]] = {
    claim => {
      EitherT.fromEither[F](for {
        parseResult <- parse(claim.content)
        accessTokenId <- parseResult.hcursor.downField("accessTokenId").as[String]
      } yield accessTokenId)
        .toOption
        .flatMap(repos.refreshTokenRepo().getByAccessTokenId(_))
        .map(refreshToken => UserId(refreshToken.userId)).value
    }
  }

  val middleware = JwtAuthMiddleware[F, UserId](jwtAuth, _ => authenticate)

  def createToken(userId: String, accessTokenId: String): AccessTokenWithExpiry = {
    val current = System.currentTimeMillis()
    val expiry = current / 1000 + TimeUnit.MILLISECONDS.toSeconds(ACCESS_TOKEN_EXPIRY)
    val claims = JwtClaim(
      expiration = Some(expiry),
      issuedAt = Some(current / 1000),
      issuer = Some("wind-alerts.com"),
      subject = Some(userId)
    ) + ("accessTokenId", accessTokenId)

    AccessTokenWithExpiry(Jwt.encode(claims, key.value, JwtAlgorithm.HS256), expiry)
  }

  def tokens(accessToken: String, refreshToken: RefreshToken, expiredAt: Long, user: UserT): TokensWithUser =
    domain.TokensWithUser(accessToken, refreshToken.refreshToken, expiredAt, user.asDTO())

}
