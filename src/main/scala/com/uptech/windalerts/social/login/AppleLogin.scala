package com.uptech.windalerts.social.login

import java.io.{DataInputStream, File}
import java.math.BigInteger
import java.security.spec.RSAPublicKeySpec
import java.security.{KeyFactory, PrivateKey}
import java.util.Base64

import com.softwaremill.sttp.{HttpURLConnectionBackend, sttp, _}
import com.turo.pushy.apns.auth.ApnsSigningKey
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain.{ApplePublicKeyList, AppleUser, TokenResponse}
import io.circe.parser
import org.log4s.getLogger
import pdi.jwt._

object AppleLogin extends App {
  def getUser(authorizationCode: String, privateKey: PrivateKey): AppleUser = {

    val req = sttp.body(Map(
      "client_id" -> "com.passiondigital.surfsup.ios",
      "client_secret" -> generateJWT(privateKey),
      "grant_type" -> "authorization_code",
      "code" -> authorizationCode,
    ))
      .post(uri"https://appleid.apple.com/auth/token?scope=email")

    implicit val backend = HttpURLConnectionBackend()

    val body = req.send().body
    getLogger.error(body.toString)
    val tokenResponse = body.flatMap(parser.parse(_)).flatMap(x => x.as[TokenResponse]).right.get
    val claims = Jwt.decode(tokenResponse.id_token, JwtOptions(signature = false))
    val parsedEither = parser.parse(claims.toOption.get.content)
    getLogger.error(claims.toOption.get.content)
    parsedEither.flatMap(x => x.as[AppleUser]).right.get
  }

  private def createPublicKeyApple() = {
    implicit val backend = HttpURLConnectionBackend()

    val applePublicKey = sttp.get(uri"https://appleid.apple.com/auth/keys").send().body.flatMap(parser.parse(_))
      .flatMap(x => x.as[ApplePublicKeyList])
      .map(list => {
        val x = list.keys.head
        val modulus = new BigInteger(1, Base64.getUrlDecoder.decode(x.n))
        val exponent = new BigInteger(1, Base64.getUrlDecoder.decode(x.e))
        KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent))
      })
    getLogger.error(applePublicKey.toString)
    applePublicKey.right.get
  }

  def generateJWT(privateKey:PrivateKey) = {
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


  def getPrivateKey(filename: String) = {
    val f = new File(filename)
    val fis = new FileInputStream(f)
    val dis = new DataInputStream(fis)
    val keyBytes = new Array[Byte](f.length.asInstanceOf[Int])
    dis.readFully(keyBytes)
    dis.close
    ApnsSigningKey.loadFromPkcs8File(new File(filename), "W9WH7WV85S", "A423X8QGF3")
  }
}