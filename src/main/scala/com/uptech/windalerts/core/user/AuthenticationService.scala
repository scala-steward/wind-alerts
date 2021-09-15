package com.uptech.windalerts.core.user

import cats.data.EitherT
import cats.effect.Effect
import com.uptech.windalerts.core.refresh.tokens.{RefreshToken, RefreshTokenRepository}
import com.uptech.windalerts.infrastructure.endpoints.dtos._
import dev.profunktor.auth.JwtAuthMiddleware
import dev.profunktor.auth.jwt.{JwtAuth, JwtSecretKey}
import io.circe.parser._
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import java.util.concurrent.TimeUnit

case class AccessTokenWithExpiry(accessToken: String, expiredAt: Long)


class AuthenticationService[F[_] : Effect](refreshTokenRepository: RefreshTokenRepository[F]) {
  val ACCESS_TOKEN_EXPIRY = 1L * 24L * 60L * 60L * 1000L

  private val key = JwtSecretKey("secretKey")
  val jwtAuth = JwtAuth.hmac("secretKey", JwtAlgorithm.HS256)

  val authenticate: JwtClaim => F[Option[UserIdMetadata]] = {
    claim => {
      EitherT.fromEither[F](for {
        parseResult <- parse(claim.content)
        accessTokenId <- parseResult.hcursor.downField("accessTokenId").as[String]
        emailId <- parseResult.hcursor.downField("emailId").as[String]
        firstName <- parseResult.hcursor.downField("firstName").as[String]
        userType <- parseResult.hcursor.downField("userType").as[String]
      } yield (accessTokenId, EmailId(emailId), UserType(userType), firstName))
        .toOption
        .flatMap(claims => refreshTokenRepository.getByAccessTokenId(claims._1)
          .map(refreshToken => UserIdMetadata(UserId(refreshToken.userId), claims._2, claims._3, claims._4))).value
    }
  }

  val middleware = JwtAuthMiddleware[F, UserIdMetadata](jwtAuth, _ => authenticate)

  def createToken(userId: UserId, emailId: EmailId, firstName: String, accessTokenId: String, userType: UserType): AccessTokenWithExpiry = {
    val current = System.currentTimeMillis()
    val expiry = current / 1000 + TimeUnit.MILLISECONDS.toSeconds(ACCESS_TOKEN_EXPIRY)
    val claims = JwtClaim(
      expiration = Some(expiry),
      issuedAt = Some(current / 1000),
      issuer = Some("wind-alerts.com"),
      subject = Some(userId.id)
    ) + ("accessTokenId", accessTokenId) + ("emailId", emailId.email) + ("firstName", firstName) + ("userType", userType.value)

    AccessTokenWithExpiry(Jwt.encode(claims, key.value, JwtAlgorithm.HS256), expiry)
  }

  def tokens(accessToken: String, refreshToken: RefreshToken, expiredAt: Long, user: UserT): TokensWithUser =
    TokensWithUser(accessToken, refreshToken.refreshToken, expiredAt, user)

}
