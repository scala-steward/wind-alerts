package com.uptech.windalerts.users

import java.util.concurrent.TimeUnit
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import com.uptech.windalerts.Repos
import com.uptech.windalerts.alerts.domain.AlertT
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.{AlertNotFoundError, OperationNotAllowed, SurfsUpError, UserNotFoundError, domain}
import dev.profunktor.auth.JwtAuthMiddleware
import dev.profunktor.auth.jwt.{JwtAuth, JwtSecretKey, JwtToken}
import io.circe.parser._
import io.scalaland.chimney.dsl._
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import scala.util.Random

case class AccessTokenWithExpiry(accessToken: String, expiredAt: Long)

trait AuthenticationService[F[_]] {
  val ACCESS_TOKEN_EXPIRY = 1L * 24L * 60L * 60L * 1000L

  def createToken(userId: String, accessTokenId: String): EitherT[F, SurfsUpError, AccessTokenWithExpiry]

  def authorizePremiumUsers(user: domain.UserT): EitherT[F, SurfsUpError, UserT]

  def authorizeAlertEditRequest(user: UserT, alertId: String, alertRequest: AlertRequest): EitherT[F, SurfsUpError, UserT]

  def tokens(accessToken: String, refreshToken: RefreshToken, expiredAt: Long, user: UserT): EitherT[F, SurfsUpError, TokensWithUser]

  def createOtp(n: Int): F[String]
}


class AuthenticationServiceImpl(repos: Repos[IO]) extends AuthenticationService[IO] {
  private val key = JwtSecretKey("secretKey")
  val jwtAuth = JwtAuth.hmac("secretKey", JwtAlgorithm.HS256)
  val authenticate: JwtClaim => IO[Option[UserId]] = {
    claim => {
      val r = for {
        parseResult <- IO.fromEither(parse(claim.content))
        accessTokenId <- IO.fromEither(parseResult.hcursor.downField("accessTokenId").as[String])
        maybeRefreshToken <- repos.refreshTokenRepo().getByAccessTokenId(accessTokenId).value
      } yield maybeRefreshToken

      r.map(f => f.map(t => UserId(t.userId)))
    }
  }

  val authenticate1: JwtToken => JwtClaim => IO[Option[UserId]] = _ => authenticate

  val middleware = JwtAuthMiddleware[IO, UserId](jwtAuth, authenticate1)

  override def createToken(userId: String, accessTokenId: String): EitherT[IO, SurfsUpError, AccessTokenWithExpiry] = {
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

  override def tokens(accessToken: String, refreshToken: RefreshToken, expiredAt: Long, user: UserT): EitherT[IO, SurfsUpError, TokensWithUser] =
    EitherT.right(IO(domain.TokensWithUser(accessToken, refreshToken.refreshToken, expiredAt, user.asDTO())))

  def createOtp(n: Int) = {
    val alpha = "0123456789"
    val size = alpha.size

    IO.pure((1 to n).map(_ => alpha(Random.nextInt.abs % size)).mkString)
  }

  override def authorizePremiumUsers(user: domain.UserT): EitherT[IO, SurfsUpError, UserT] = {
    EitherT.fromEither(if (UserType(user.userType) == UserType.Premium || UserType(user.userType) == UserType.Trial) {
      Right(user)
    } else {
      Left(OperationNotAllowed(s"Please subscribe to perform this action"))
    })
  }

  override def authorizeAlertEditRequest(user: UserT, alertId: String, alertRequest: AlertRequest): EitherT[IO, SurfsUpError, UserT] = {
    EitherT.liftF(repos.alertsRepository().getAllForUser(user._id.toHexString))
      .flatMap(alerts => EitherT.fromEither(authorizeAlertEditRequest(user, alertId, alerts, alertRequest)))
  }

  private def authorizeAlertEditRequest(user: UserT, alertId: String, alert: AlertsT, alertRequest: AlertRequest): Either[OperationNotAllowed, UserT] = {
    if (UserType(user.userType) == UserType.Premium || UserType(user.userType) == UserType.Trial) {
      Right(user)
    } else {
      if (checkForNonPremiumUser(alertId, alert, alertRequest)) Right(user)
      else Left(OperationNotAllowed(s"Please subscribe to perform this action"))
    }
  }

  def checkForNonPremiumUser(alertId: String, alerts: AlertsT, alertRequest: AlertRequest) = {
    val alertOption = alerts.alerts.sortBy(_.createdAt).headOption


    alertOption.map(alert=> {
      if (alert._id.toHexString != alertId) {
        false
      } else {
        allFieldExceptStatusAreSame(alert, alertRequest)
      }
    }).getOrElse(false)
  }


  def allFieldExceptStatusAreSame(alert: AlertT, alertRequest: AlertRequest) = {
      alert.days.sorted == alertRequest.days.sorted &&
      alert.swellDirections.sorted == alertRequest.swellDirections.sorted &&
      alert.timeRanges.sortBy(_.from) == alertRequest.timeRanges.sortBy(_.from) &&
      alert.waveHeightFrom == alertRequest.waveHeightFrom &&
      alert.waveHeightTo == alertRequest.waveHeightTo &&
      alert.windDirections.sorted == alertRequest.windDirections.sorted &&
      alert.tideHeightStatuses.sorted == alertRequest.tideHeightStatuses.sorted
      alert.timeZone == alertRequest.timeZone
  }
}
