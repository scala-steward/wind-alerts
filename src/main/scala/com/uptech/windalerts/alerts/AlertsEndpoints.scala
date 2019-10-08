package com.uptech.windalerts.alerts

import cats.data.OptionT
import cats.effect.IO
import com.uptech.windalerts.domain.HttpErrorHandler
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain.{AlertRequest, UserId}
import org.http4s.AuthedRoutes
import org.http4s.dsl.Http4sDsl


class AlertsEndpoints(alertService: AlertsService.Service, httpErrorHandler: HttpErrorHandler[IO]) extends Http4sDsl[IO]  {

  def authedService(): AuthedRoutes[UserId, IO] =
    AuthedRoutes {
      case GET -> Root  as user => {
        val resp = alertService.getAllForUser(user.id)
        val either = resp.attempt.unsafeRunSync()
        val response = either.fold(httpErrorHandler.handleThrowable, _ => Ok(either.right.get))
        OptionT.liftF(response)
      }

      case authReq@POST -> Root  as user => {
        val response = authReq.req.decode[AlertRequest] { alert =>
          val saved = alertService.save(alert, user.id)
          val either = saved.attempt.unsafeRunSync()
          either.fold(httpErrorHandler.handleThrowable, _ => Created(either.right.get))
        }
        OptionT.liftF(response)
      }

      case DELETE -> Root / alertId as user => {
        val eitherDeleted = alertService.delete(user.id, alertId)
        val eitherDeletedUnsafe = eitherDeleted.attempt.unsafeRunSync()
        val response = if (eitherDeletedUnsafe.isLeft) {
          httpErrorHandler.handleThrowable(eitherDeletedUnsafe.left.get)
        } else {
          eitherDeletedUnsafe.right.get match {
            case Left(value) => httpErrorHandler.handleThrowable(value)
            case Right(_) => NoContent()
          }
        }

        OptionT.liftF(response)
      }

      case authReq@PUT -> Root / alertId as user => {
        val response = authReq.req.decode[AlertRequest] { alert =>
          val updated = alertService.update(user.id, alertId, alert)
          val resp = updated.unsafeRunSync()
          Ok(resp.toOption.get.unsafeRunSync())
        }
        OptionT.liftF(response)
      }

    }
}
