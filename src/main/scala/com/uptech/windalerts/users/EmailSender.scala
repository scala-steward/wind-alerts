package com.uptech.windalerts.users

import java.util.Properties

import javax.mail.internet.MimeMessage
import javax.mail._
import org.log4s.getLogger

class EmailSender(username: String, password: String) {
  private val logger = getLogger


  def sendOtp(to: String, otp: String) = {
    try {
      val prop = new Properties
      prop.put("mail.smtp.host", "smtp.mailgun.org")
      prop.put("mail.smtp.port", "587")
      prop.put("mail.smtp.auth", "true")
      prop.put("mail.smtp.starttls.enable", "true")
      logger.error(s"prop   $prop")
      val session = Session.getInstance(prop, new Authenticator() {
        override protected def getPasswordAuthentication = new PasswordAuthentication(username, password)
      })
      val message = new MimeMessage(session)
      message.setRecipients(Message.RecipientType.TO, to)
      message.setSubject("Verify SurfsUp account")
      message.setText(s"$otp")
      logger.error(s"message   $message")
      Transport.send(message)
      logger.error(s"Sent otp to $to")
    } catch {
      case e:Throwable => logger.error(s"Exception sending email $e , ${e.printStackTrace()}")
    }
  }

}
