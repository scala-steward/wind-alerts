package com.uptech.windalerts.infrastructure

import cats.Monad
import cats.data.EitherT
import com.softwaremill.sttp.{HttpURLConnectionBackend, sttp, _}
import com.uptech.windalerts.logger


class EmailSender[F[_]](apiKey: String) {
  def sendOtp(to: String, otp: String)(implicit F: Monad[F]) = {
    send(to, 1l,
      s"""
            "code": "$otp",
            "expiry": 60
       """.stripMargin)
  }

  def sendResetPassword(firstName: String, to: String, password: String)(implicit F: Monad[F]) = {
    send(to, 3l,
      s"""
            "password": "${password}",
            "firstName": "$firstName",
            "expiry": 24
       """.stripMargin)
  }

  def send(to: String, templateId: Long, params: String)(implicit F: Monad[F]) = {
    EitherT.fromEither({
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
        .post(uri"https://api.sendinblue.com/v3/smtp/email")

      implicit val backend = HttpURLConnectionBackend()

      val body = req.send().body
      logger.info(s"Response from sendinblue ${body.toString}")
      body
    }
    )

  }
}

