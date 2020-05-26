package com.uptech.windalerts.users

import cats.effect.{ContextShift, Effect}
import cats.implicits._
import com.uptech.windalerts.domain.HttpErrorHandler
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import cats.implicits._

class UpdateUserRolesEndpoints[F[_]: Effect](userService: UserService[F],
                               httpErrorHandler: HttpErrorHandler[F])(implicit cs: ContextShift[F]) extends Http4sDsl[F] {
  def endpoints(): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case _@GET -> Root / "update" / "trial" =>
        val action = for {
          response <- userService.updateTrialUsers()
        } yield response
        action.value.flatMap {
          case Right(_) => Ok()
          case Left(error) => httpErrorHandler.handleThrowable(error)
        }

      case _@GET -> Root / "update" / "subscribed" / "android" =>
        val action = for {
          response <- userService.updateAndroidSubscribedUsers()
        } yield response
        action.value.flatMap {
          case Right(_) => Ok()
          case Left(error) => httpErrorHandler.handleThrowable(error)
        }

      case _@GET -> Root / "update" / "subscribed" / "apple" =>
        val action = for {
          response <- userService.updateAppleSubscribedUsers()
        } yield response
        action.value.flatMap {
          case Right(_) => Ok()
          case Left(error) => httpErrorHandler.handleThrowable(error)
        }
    }

}
