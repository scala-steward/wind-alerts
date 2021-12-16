package com.uptech.windalerts.infrastructure.endpoints

import cats.Applicative
import cats.effect.{IO, Sync}
import cats.implicits._
import com.uptech.windalerts.logger
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpApp, Service, Status}
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
class errors[F[_] : Sync]() extends Http4sDsl[F]  {
  def errorMapper(service: HttpApp[F])(implicit M: Applicative[F]): HttpApp[F] = Service.lift { req =>
    service(req).map {
      case Status.Successful(resp) => resp
      case resp => {
        resp.withEntity(resp.bodyAsText.map(s=>{
          logger.warn(s)
          if (s.equals("not found")) "Invalid access token" else s
        }))
      }
    }
  }
}
