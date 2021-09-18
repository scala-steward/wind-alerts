package com.uptech.windalerts.infrastructure.endpoints

import cats.effect.IO
import net.logstash.logback.argument.StructuredArguments.kv
import org.http4s.{HttpApp, Service, Status}
import com.uptech.windalerts.logger
object errors {

  def errorMapper(service: HttpApp[IO]): HttpApp[IO] = Service.lift { req =>
    service(req).map {
      case Status.Successful(resp) => {
        resp
      }
      case resp => {
        resp.withEntity(resp.bodyAsText.map(s=>{
          logger.warn(s)
          if (s.equals("not found")) "Invalid access token" else s
        }))
      }
    }
  }
}
