package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.core.notifications.{Notification, NotificationRepository, UserWithNotificationsCount}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters._

import scala.concurrent.ExecutionContext.Implicits.global

class MongoNotificationsRepository(notifications: MongoCollection[Notification])(implicit cs: ContextShift[IO]) extends  NotificationRepository[IO] {
  def create(notification: Notification) = {
    IO.fromFuture(IO(notifications.insertOne(notification).toFuture().map(_=>notification)))
  }

  def countNotificationInLastHour(userId: String): EitherT[IO, Exception, UserWithNotificationsCount] = {
    EitherT.liftF(for {
      all <- IO.fromFuture(IO(notifications.find(and(equal("userId", userId), gte("sentAt", System.currentTimeMillis() - (60 * 60 * 1000)))).collect().toFuture()))
    } yield UserWithNotificationsCount(userId, all.size))
  }
}
