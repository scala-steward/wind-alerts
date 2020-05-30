package com.uptech.windalerts.users

import java.util.Properties

import cats.{Functor, Monad}
import javax.mail.internet.MimeMessage
import javax.mail._
import org.log4s.getLogger

class EmailSender[F[_]](username: String, password: String) {
  private val logger = getLogger


  def sendOtp(to: String, otp: String)(implicit F:Monad[F]):F[Unit] = {
    F.pure(send(to, "Verify SurfsUp account", otp))
  }

  def send(to: String, subject: String, text:String)(implicit F:Monad[F]) = {
    F.pure(try {
      val prop = new Properties
      prop.put("mail.smtp.host", "smtp-relay.sendinblue.com")
      prop.put("mail.smtp.port", "587")
      prop.put("mail.smtp.auth", "true")
      prop.put("mail.smtp.starttls.enable", "true")
      prop.put("mail.smtp.from", username)

      logger.error(s"prop   $prop")
      val session = Session.getInstance(prop, new Authenticator() {
        override protected def getPasswordAuthentication = new PasswordAuthentication(username, password)
      })
      val message = new MimeMessage(session)
      message.setRecipients(Message.RecipientType.TO, to)
      message.setSubject(subject)
      message.setText(text)
      message.setFrom(username)
      logger.error(s"message   $message")
      Transport.send(message)
      logger.error(s"Sent message to $to")
    } catch {
      case e:Throwable => logger.error(s"Exception sending email $e , ${e.printStackTrace()}")
    })
  }

}
