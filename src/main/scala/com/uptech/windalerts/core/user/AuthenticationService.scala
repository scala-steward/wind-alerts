package com.uptech.windalerts.core.user

import cats.data.OptionT
import cats.effect.Effect
import dev.profunktor.auth.JwtAuthMiddleware
import dev.profunktor.auth.jwt.{JwtAuth, JwtSecretKey}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import java.util.concurrent.TimeUnit

case class AccessTokenWithExpiry(accessToken: String, expiredAt: Long)
object AccessTokenWithExpiry {
  val ACCESS_TOKEN_EXPIRY = 6L * 60L * 60L * 1000L

  def apply(key:JwtSecretKey, userId: UserId): AccessTokenWithExpiry = {
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

class AuthenticationService[F[_] : Effect](jwtKey:String, userRepository: UserRepository[F]) {
  private val key = JwtSecretKey(jwtKey)
  val jwtAuth = JwtAuth.hmac(jwtKey, JwtAlgorithm.HS256)

  val authenticate: JwtClaim => F[Option[UserIdMetadata]] = claim => {
    OptionT.fromOption(claim.subject)
      .flatMap(userRepository.getByUserId(_))
      .map(_.userIdMetadata())
      .value
  }

  val middleware = JwtAuthMiddleware[F, UserIdMetadata](jwtAuth, _ => authenticate)

  def createToken(userId: UserId): AccessTokenWithExpiry = AccessTokenWithExpiry(key, userId)
}
