package com.uptech.windalerts.infrastructure

import cats.effect.Sync
import cats.{Applicative, Monad}
import com.softwaremill.sttp.{HttpURLConnectionBackend, sttp, _}
import com.uptech.windalerts.core.otp.OTPNotifier
import com.uptech.windalerts.core.user.PasswordNotifier
import com.uptech.windalerts.logger


class SendInBlueEmailSender[F[_] : Sync](apiKey: String) extends OTPNotifier[F] with PasswordNotifier[F] {
  override def notifyOTP(to: String, otp: String)(implicit F: Monad[F]): F[String] = {
    send(to, 1l,
      s"""
            "code": "$otp",
            "expiry": 60
       """.stripMargin)
  }

  override def notifyNewPassword(firstName: String, to: String, password: String) = {
    send(to, 3l,
      s"""
            "password": "${password}",
            "firstName": "$firstName",
            "expiry": 24
       """.stripMargin)
  }

  def send(to: String, templateId: Long, params: String) = {
    val requestBody =
      s"""{
           "to": [
               {
                   "email": "$to"
               }
           ],
           "templateId": $templateId,
           "params": {
               $params
           }
       }"""

    val req = sttp.body(
      requestBody.stripMargin).header("api-key", apiKey)
      .contentType("application/json")
      .acceptEncoding("application/json")
      .post(uri"https://api.sendinblue.com/v3/smtp/email")

    implicit val backend = HttpURLConnectionBackend()

    val body = req.send().body

    Applicative[F].pure(body.toOption.get)
  }
}

