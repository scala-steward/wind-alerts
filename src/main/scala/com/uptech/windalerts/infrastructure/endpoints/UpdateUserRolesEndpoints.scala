package com.uptech.windalerts.infrastructure.endpoints

import cats.effect.{ContextShift, Effect}
import cats.implicits._
import cats.mtl.Handle
import cats.mtl.implicits.toHandleOps
import com.uptech.windalerts.core.{TokenNotFoundError, UserNotFoundError}
import com.uptech.windalerts.core.user.UserRolesService
import com.uptech.windalerts.infrastructure.endpoints.errors.mapError
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

class UpdateUserRolesEndpoints[F[_] : Effect](userRoles: UserRolesService[F])(implicit FR: Handle[F, Throwable]) extends Http4sDsl[F] {
  def endpoints(): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case _@GET -> Root / "update" / "trial" =>
        val action = for {
          response <- userRoles.updateTrialUsers()
        } yield response
        action.flatMap(
          _ => Ok()
        ).handle[Throwable](mapError(_))

      case _@GET -> Root / "update" / "subscribed" / "android" =>
        val action = for {
          response <- userRoles.updateSubscribedUsers()
        } yield response
        action.flatMap(
          _ => Ok()
        ).handle[Throwable](mapError(_))

      case _@GET -> Root / "update" / "subscribed" / "apple" =>
        val action = for {
          response <- userRoles.updateSubscribedUsers()
        } yield response
        action.flatMap(
          _ => Ok()
        ).handle[Throwable](mapError(_))
    }

}
