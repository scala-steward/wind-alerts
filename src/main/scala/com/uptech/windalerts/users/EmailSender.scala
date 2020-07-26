package com.uptech.windalerts.users

import cats.Monad
import com.softwaremill.sttp.{HttpURLConnectionBackend, sttp, _}
import org.log4s.getLogger


class EmailSender[F[_]](apiKey: String) {
  private val logger = getLogger

  def sendOtp(to: String, otp: String)(implicit F: Monad[F]) = {
    send(to, 1l,
      s"""
            "code": "$otp",
            "expiry": 60
       """.stripMargin)
  }

  def sendResetPassword(firstName:String, to: String, password: String)(implicit F: Monad[F]) = {
    send(to, 3l,
      s"""
            "password": "${password}",
            "firstName": "$firstName",
            "expiry": 24
       """.stripMargin)
  }

  def send(to: String, templateId: Long, params: String)(implicit F: Monad[F]) = {
    F.pure(try {
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
      println(requestBody)

      val req = sttp.body(
        requestBody.stripMargin).header("api-key", apiKey)
        .contentType("application/json")
        .post(uri"https://api.sendinblue.com/v3/smtp/email")

      implicit val backend = HttpURLConnectionBackend()

      val body = req.send().body
      getLogger.error(body.toString)

    } catch {
      case e: Throwable => logger.error(s"Exception sending email $e , ${e.printStackTrace()}")
    })

  }
}

