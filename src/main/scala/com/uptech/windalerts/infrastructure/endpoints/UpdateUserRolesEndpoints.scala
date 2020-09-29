package com.uptech.windalerts.infrastructure.endpoints

import cats.effect.{ContextShift, Effect}
import cats.implicits._
import com.uptech.windalerts.domain.HttpErrorHandler
import com.uptech.windalerts.users.UserRolesService
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

class UpdateUserRolesEndpoints[F[_]: Effect](userRoles: UserRolesService[F],
                                             httpErrorHandler: HttpErrorHandler[F])(implicit cs: ContextShift[F]) extends Http4sDsl[F] {
  def endpoints(): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case _@GET -> Root / "update" / "trial" =>
        val action = for {
          response <- userRoles.updateTrialUsers()
        } yield response
        action.value.flatMap {
          case Right(_) => Ok()
          case Left(error) => httpErrorHandler.handleThrowable(error)
        }

      case _@GET -> Root / "update" / "subscribed" / "android" =>
        val action = for {
          response <- userRoles.updateAndroidSubscribedUsers()
        } yield response
        action.value.flatMap {
          case Right(_) => Ok()
          case Left(error) => httpErrorHandler.handleThrowable(error)
        }

      case _@GET -> Root / "update" / "subscribed" / "apple" =>
        val action = for {
          response <- userRoles.updateAppleSubscribedUsers()
        } yield response
        action.value.flatMap {
          case Right(_) => Ok()
          case Left(error) => httpErrorHandler.handleThrowable(error)
        }
    }

}
