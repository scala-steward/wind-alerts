package com.uptech.windalerts.domain

import cats.effect.IO
import com.uptech.windalerts.users.ValidationError
import org.http4s.{HttpApp, Service, Status}
import org.log4s.getLogger

object errors {

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
