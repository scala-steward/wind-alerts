package com.uptech.windalerts.notifications

import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.{Notification, UserWithCount}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters._

import scala.concurrent.ExecutionContext.Implicits.global

class MongoNotificationsRepository(notifications: MongoCollection[Notification])(implicit cs: ContextShift[IO]) extends  NotificationRepository[IO] {
  def create(notification: domain.Notification) = {
    IO.fromFuture(IO(notifications.insertOne(notification).toFuture().map(_=>notification)))
  }

  def countNotificationInLastHour(userId: String): EitherT[IO, Exception, domain.UserWithCount] = {
    EitherT.liftF(for {
      all <- IO.fromFuture(IO(notifications.find(and(equal("userId", userId), gte("sentAt", System.currentTimeMillis() - (60 * 60 * 1000)))).collect().toFuture()))
    } yield UserWithCount(userId, all.size))
  }
}
