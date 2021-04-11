package com.uptech.windalerts.infrastructure.endpoints

import cats.effect.Effect
import com.uptech.windalerts.core.notifications.Notifications
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._

class NotificationEndpoints[F[_] : Effect](notifications: Notifications) extends Http4sDsl[F] {
  def allRoutes() =
    routes().orNotFound

  def routes() = {
    HttpRoutes.of[F] {
      case GET -> Root / "notify" => {
        notify()
      }
      case GET -> Root => {
        notify()
      }
    }
  }

  private def notify() = {
    val res = notifications.sendNotification
    val _ = res.value.unsafeRunSync()
    Ok()
  }
}