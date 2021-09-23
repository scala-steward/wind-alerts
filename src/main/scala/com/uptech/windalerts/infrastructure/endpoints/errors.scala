package com.uptech.windalerts.infrastructure.endpoints

import cats.{Applicative, Functor, Monad}
import cats.effect.{Effect, IO}
import com.uptech.windalerts.core.alerts.AlertsService
import net.logstash.logback.argument.StructuredArguments.kv
import org.http4s.{HttpApp, Service, Status}
import com.uptech.windalerts.logger
import org.http4s.dsl.Http4sDsl

import cats.Bifunctor.ops.toAllBifunctorOps
import cats.data.EitherT
import cats.effect.Sync
import cats.{Functor, Monad}
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.user.{UserId, UserType}
import com.uptech.windalerts.core.{AlertNotFoundError, OperationNotAllowed, SurfsUpError}
import com.uptech.windalerts.infrastructure.endpoints.dtos.{AlertDTO, AlertRequest}
import cats.Applicative
import cats.data.{EitherT, OptionT}
import cats.effect.Sync
import cats.implicits._
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
