package com.uptech.windalerts.infrastructure.social.login

import java.io.{DataInputStream, File}
import java.security.PrivateKey
import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.softwaremill.sttp.{HttpURLConnectionBackend, sttp, _}
import com.turo.pushy.apns.auth.ApnsSigningKey
import com.uptech.windalerts.core.social.login.{AppleAccessRequest, SocialPlatform, SocialUser}
import com.uptech.windalerts.domain.UserNotFoundError
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain.{AppleUser, SurfsUpEitherT, TokenResponse}
import io.circe.parser
import org.log4s.getLogger
import pdi.jwt._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class ApplePlatform(filename: String)(implicit cs: ContextShift[IO]) extends SocialPlatform[IO, AppleAccessRequest] {
  val privateKey = getPrivateKey(filename)

  override def fetchUserFromPlatform(credentials: AppleAccessRequest): SurfsUpEitherT[IO, SocialUser] = {
    fetchUserFromPlatform_(credentials).leftMap(_=>UserNotFoundError())
  }

  private def fetchUserFromPlatform_(credentials: AppleAccessRequest): SurfsUpEitherT[IO, SocialUser]  = {
    EitherT.liftF(IO.fromFuture(IO(Future(getUser(credentials.authorizationCode)))))
      .map(appleUser => SocialUser(appleUser.sub, appleUser.email, credentials.deviceType, credentials.deviceToken, credentials.name))
  }

  def getUser(authorizationCode: String): AppleUser = {
    getUser(authorizationCode, privateKey)
  }

  private def getUser(authorizationCode: String, privateKey: PrivateKey): AppleUser = {
    val req = sttp.body(Map(
      "client_id" -> "com.passiondigital.surfsup.ios",
      "client_secret" -> generateJWT(privateKey),
      "grant_type" -> "authorization_code",
      "code" -> authorizationCode,
    ))
      .post(uri"https://appleid.apple.com/auth/token?scope=email")

    implicit val backend = HttpURLConnectionBackend()

    val responseBody = req.send().body
    getLogger.error(responseBody.toString)
    val tokenResponse = responseBody.flatMap(parser.parse(_)).flatMap(x => x.as[TokenResponse]).right.get
    val claims = Jwt.decode(tokenResponse.id_token, JwtOptions(signature = false))
    val parsedEither = parser.parse(claims.toOption.get.content)
    getLogger.error(claims.toOption.get.content)
    parsedEither.flatMap(x => x.as[AppleUser]).right.get
  }

  private def generateJWT(privateKey:PrivateKey) = {
    val current = System.currentTimeMillis()
    val claims = JwtClaim(
      issuer = Some("W9WH7WV85S"),
      audience = Some(Set("https://appleid.apple.com")),
      subject = Some("com.passiondigital.surfsup.ios"),
      expiration = Some(System.currentTimeMillis() / 1000 + (60 * 5)),
      issuedAt = Some(current / 1000)
    )
    val header = JwtHeader(JwtAlgorithm.ES256).withType(null).withKeyId("A423X8QGF3")
    Jwt.encode(header.toJson, claims.toJson, privateKey, JwtAlgorithm.ES256)
  }

  import java.io.FileInputStream


  private def getPrivateKey(filename: String) = {
    val f = new File(filename)
    val fis = new FileInputStream(f)
    val dis = new DataInputStream(fis)
    val keyBytes = new Array[Byte](f.length.asInstanceOf[Int])
    dis.readFully(keyBytes)
    dis.close
    ApnsSigningKey.loadFromPkcs8File(new File(filename), "W9WH7WV85S", "A423X8QGF3")
  }

}