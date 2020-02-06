package com.uptech.windalerts.notifications

import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.{MNotification, UserWithCount}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters._

class MongoNotificationsRepository(notifications: MongoCollection[MNotification])(implicit cs: ContextShift[IO]) {
  def create(notification: domain.MNotification) = {
    IO({
      notifications.insertOne(notification).toFuture().value.get
      notification
    })
  }

  def countNotificationInLastHour(userId: String): EitherT[IO, Exception, domain.UserWithCount] = {
    EitherT.liftF(for {
      all <- IO.fromFuture(IO(notifications.find(and(equal("userId", userId), gte("sentAt", System.currentTimeMillis() - (60 * 60 * 1000)))).collect().toFuture()))
    } yield UserWithCount(userId, all.size))
  }
}
