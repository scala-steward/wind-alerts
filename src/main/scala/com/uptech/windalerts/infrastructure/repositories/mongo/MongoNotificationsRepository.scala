package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.Monad
import cats.effect.{Async, ContextShift}
import cats.implicits._
import com.uptech.windalerts.core.notifications.{Notification, NotificationRepository, UserWithNotificationsCount}
import io.scalaland.chimney.dsl._
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters._

import scala.concurrent.ExecutionContext.Implicits.global

class MongoNotificationsRepository[F[_]](notifications: MongoCollection[DBNotification])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends NotificationRepository[F] {
  def create(alertId: String, userId: String, deviceToken: String, sentAt: Long) = {
    val dbNotification = DBNotification(alertId, userId, deviceToken, sentAt)
    Async.fromFuture(M.pure(notifications.insertOne(dbNotification).toFuture().map(_ => dbNotification.toNotification)))
  }

  def countNotificationsInLastHour(userId: String): F[UserWithNotificationsCount] = {
    (for {
      all <- Async.fromFuture(M.pure(notifications.find(and(equal("userId", userId), gte("sentAt", System.currentTimeMillis() - (60 * 60 * 1000)))).collect().toFuture()))
    } yield UserWithNotificationsCount(userId, all.size))
  }
}


case class DBNotification(_id: ObjectId, alertId: String, userId: String, deviceToken: String, sentAt: Long) {
  def toNotification(): Notification = {
    this.into[Notification]
      .withFieldComputed(_.id, dbNotification => dbNotification._id.toHexString)
      .transform
  }
}

object DBNotification {
  def apply(alertId: String, userId: String, deviceToken: String, sentAt: Long): DBNotification
  = new DBNotification(new ObjectId(), alertId, userId, deviceToken, sentAt)
}