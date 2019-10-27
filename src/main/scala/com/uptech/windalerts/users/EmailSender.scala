package com.uptech.windalerts.users

import java.util.Properties

import javax.mail.internet.MimeMessage
import javax.mail._
import org.log4s.getLogger

class EmailSender(username: String, password: String, baseUrl: String) {
  private val logger = getLogger


  def sendVerificationEmail(to: String, token: String) = {
    try {
      val prop = new Properties
      prop.put("mail.smtp.host", "smtp.gmail.com")
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
      message.setText(s"$baseUrl$token")
      logger.error(s"message   $message")
      Transport.send(message)
      logger.error(s"Sent email")
    } catch {
      case e:Throwable => logger.error(s"Exception sending email $e , ${e.printStackTrace()}")
    }
  }

}
