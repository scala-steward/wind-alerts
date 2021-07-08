package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.Monad
import cats.data.EitherT
import cats.effect.{Async, ContextShift, IO}
import com.uptech.windalerts.core.notifications.{Notification, NotificationRepository, UserWithNotificationsCount}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters._

import scala.concurrent.ExecutionContext.Implicits.global
import cats.implicits._

class MongoNotificationsRepository[F[_]](notifications: MongoCollection[Notification])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends  NotificationRepository[F] {
  def create(notification: Notification) = {
    Async.fromFuture(M.pure(notifications.insertOne(notification).toFuture().map(_=>notification)))
  }

  def countNotificationInLastHour(userId: String): EitherT[F, Exception, UserWithNotificationsCount] = {
    EitherT.liftF(for {
      all <- Async.fromFuture(M.pure(notifications.find(and(equal("userId", userId), gte("sentAt", System.currentTimeMillis() - (60 * 60 * 1000)))).collect().toFuture()))
    } yield UserWithNotificationsCount(userId, all.size))
  }
}
