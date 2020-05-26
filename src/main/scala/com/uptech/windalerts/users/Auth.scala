package com.uptech.windalerts.users

import java.util.concurrent.TimeUnit

import cats.data.{EitherT, OptionT}
import cats.effect.IO
import com.uptech.windalerts.Repos
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.UserType.{Premium, Trial}
import com.uptech.windalerts.domain.domain._
import dev.profunktor.auth.JwtAuthMiddleware
import dev.profunktor.auth.jwt.{JwtAuth, JwtSecretKey, JwtToken}
import io.circe.parser._
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import io.scalaland.chimney.dsl._

import scala.util.Random

class Auth(repos: Repos[IO]) {

  val REFRESH_TOKEN_EXPIRY = 14L * 24L * 60L * 60L * 1000L
  val ACCESS_TOKEN_EXPIRY = 1L * 24L * 60L * 60L * 1000L

  case class AccessTokenWithExpiry(accessToken: String, expiredAt: Long)

  private val key = JwtSecretKey("secretKey")
  val jwtAuth = JwtAuth.hmac("secretKey", JwtAlgorithm.HS256)
  val authenticate: JwtClaim => IO[Option[UserId]] = {
        claim => {
          val r = for {
            parseResult <- IO.fromEither(parse(claim.content))
            accessTokenId <- IO.fromEither(parseResult.hcursor.downField("accessTokenId").as[String])
            maybeRefreshToken <- repos.refreshTokenRepo().getByAccessTokenId(accessTokenId).value
          } yield maybeRefreshToken

          r.map(f=>f.map(t=>UserId(t.userId)))
        }
      }

  val authenticate1: JwtToken => JwtClaim => IO[Option[UserId]] = _ => authenticate

  val middleware = JwtAuthMiddleware[IO, UserId](jwtAuth, authenticate1)

  def createToken(userId: String, accessTokenId: String): EitherT[IO, ValidationError, AccessTokenWithExpiry] = {
    val current = System.currentTimeMillis()
    val expiry = current / 1000 + TimeUnit.MILLISECONDS.toSeconds(ACCESS_TOKEN_EXPIRY)
    val claims = JwtClaim(
      expiration = Some(expiry),
      issuedAt = Some(current / 1000),
      issuer = Some("wind-alerts.com"),
      subject = Some(userId)
    ) + ("accessTokenId", accessTokenId)

    EitherT.right(IO(AccessTokenWithExpiry(Jwt.encode(claims, key.value, JwtAlgorithm.HS256), expiry)))
  }

  def tokens(accessToken: String, refreshToken: RefreshToken, expiredAt: Long, user:UserT): EitherT[IO, ValidationError, TokensWithUser] =
    EitherT.right(IO(domain.TokensWithUser(accessToken, refreshToken.refreshToken, expiredAt, user.into[UserDTO].withFieldComputed(_.id, u=>u._id.toHexString).transform)))

  def createOtp(n: Int) = {
    val alpha = "0123456789"
    val size = alpha.size

    (1 to n).map(_ => alpha(Random.nextInt.abs % size)).mkString
  }


  def generateRandomString(n: Int) = {
    val alpha = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val size = alpha.size

    (1 to n).map(_ => alpha(Random.nextInt.abs % size)).mkString
  }

  def authorizePremiumUsers(user: domain.UserT):EitherT[IO, ValidationError, UserT] = {
    EitherT.fromEither(if (UserType(user.userType) == UserType.Premium || UserType(user.userType) == UserType.Trial) {
      Right(user)
    } else {
      Left(OperationNotAllowed(s"Please subscribe to perform this action"))
    })
  }
}
