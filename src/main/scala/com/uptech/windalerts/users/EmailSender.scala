package com.uptech.windalerts.users

import java.util.Properties

import javax.mail.{Authenticator, PasswordAuthentication}
import javax.mail.{Message, Session, Transport}
import javax.mail.internet.{InternetAddress, MimeMessage}
import org.log4s.getLogger

class EmailSender(username: String, password: String, baseUrl: String) extends App {
  private val logger = getLogger


  def sendVerificationEmail(to: String, token: String) = {
    try {
      val prop = new Properties
      prop.put("mail.smtp.host", "smtp.gmail.com")
      prop.put("mail.smtp.port", "587")
      prop.put("mail.smtp.auth", "true")
      prop.put("mail.smtp.starttls.enable", "true")

      val session = Session.getInstance(prop, new Authenticator() {
        override protected def getPasswordAuthentication = new PasswordAuthentication(username, password)
      })
      val message = new MimeMessage(session)
      message.setFrom(new InternetAddress("from@gmail.com"))
      message.setRecipients(Message.RecipientType.TO, to)
      message.setSubject("Verify SurfsUp account")
      message.setText(s"$baseUrl$token")
      Transport.send(message)
    } catch {
      case e:Throwable => logger.error(s"Exception sending email $e , ${e.printStackTrace()}")
    }
  }

}
