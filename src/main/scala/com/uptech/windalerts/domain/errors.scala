package com.uptech.windalerts.domain

import cats.effect.IO
import org.http4s.{HttpApp, Service, Status}
import org.log4s.getLogger

object errors {
  trait WindAlertError extends RuntimeException
  case class UserNotFound(userName: String) extends WindAlertError
  case class OperationNotPermitted(message:String) extends WindAlertError
  case class RecordNotFound(message:String) extends WindAlertError
  case class HeaderNotPresent(message:String) extends WindAlertError
  case class UserAlreadyRegistered(message:String) extends WindAlertError
  case class InvalidCredentials(message:String) extends WindAlertError

  case class NoSuchUser(message:String) extends WindAlertError

  def errorMapper(service: HttpApp[IO]): HttpApp[IO] = Service.lift { req =>
    service(req).map {
      case Status.Successful(resp) => {
        resp
      }
      case resp => {
        resp.withEntity(resp.bodyAsText.map(s=>{
          getLogger.error(s)
          if (s.equals("not found")) "Invalid access token" else s
        }))
      }
    }
  }
}
