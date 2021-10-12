package com.uptech.windalerts.core.user

import cats.data.{EitherT, OptionT}
import cats.effect.Effect
import com.uptech.windalerts.core.refresh.tokens.{UserSessionRepository, UserSession}
import com.uptech.windalerts.infrastructure.endpoints.dtos._
import com.uptech.windalerts.logger
import dev.profunktor.auth.JwtAuthMiddleware
import dev.profunktor.auth.jwt.{JwtAuth, JwtSecretKey}
import io.circe.parser._
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import java.util.concurrent.TimeUnit

case class AccessTokenWithExpiry(accessToken: String, expiredAt: Long)


class AuthenticationService[F[_] : Effect]() {
  val ACCESS_TOKEN_EXPIRY = 6L * 60L * 60L * 1000L

  private val key = JwtSecretKey("secretKey")
  val jwtAuth = JwtAuth.hmac("secretKey", JwtAlgorithm.HS256)

  val authenticate: JwtClaim => F[Option[UserIdMetadata]] = {
    claim => {
      EitherT.fromEither[F](for {
        parseResult <- parse(claim.content)
        emailId <- parseResult.hcursor.downField("emailId").as[String]
        firstName <- parseResult.hcursor.downField("firstName").as[String]
        userType <- parseResult.hcursor.downField("userType").as[String]
      } yield (EmailId(emailId), UserType(userType), firstName))
        .leftMap(e=>{
          logger.error("Error while authenticating request", e)
          e
        })
        .toOption
        .flatMap(claims => OptionT.fromOption(claim.subject)
          .map(userId => UserIdMetadata(UserId(userId), claims._1, claims._2, claims._3))).value
    }
  }

  val middleware = JwtAuthMiddleware[F, UserIdMetadata](jwtAuth, _ => authenticate)

  def createToken(userId: UserId, emailId: EmailId, firstName: String, userType: UserType): AccessTokenWithExpiry = {
    val current = System.currentTimeMillis()
    val expiry = current / 1000 + TimeUnit.MILLISECONDS.toSeconds(ACCESS_TOKEN_EXPIRY)
    val claims = JwtClaim(
      expiration = Some(expiry),
      issuedAt = Some(current / 1000),
      issuer = Some("wind-alerts.com"),
      subject = Some(userId.id)
    ) +  ("emailId", emailId.email) + ("firstName", firstName) + ("userType", userType.value)

    AccessTokenWithExpiry(Jwt.encode(claims, key.value, JwtAlgorithm.HS256), expiry)
  }

  def tokens(accessToken: String, refreshToken: UserSession, expiredAt: Long, user: UserT): TokensWithUser =
    TokensWithUser(accessToken, refreshToken.refreshToken, expiredAt, user)

}
