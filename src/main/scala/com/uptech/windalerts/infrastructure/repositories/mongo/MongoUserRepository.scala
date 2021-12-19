package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.Monad
import cats.data.OptionT
import cats.effect.{Async, ContextShift}
import cats.implicits.toFunctorOps
import com.uptech.windalerts.core.user.UserType.{Premium, Trial}
import com.uptech.windalerts.core.user.{UserRepository, UserT}
import com.uptech.windalerts.infrastructure.endpoints.dtos.UserRequest
import io.scalaland.chimney.dsl._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal, lt}

import scala.concurrent.ExecutionContext.Implicits.global

class MongoUserRepository[F[_]](collection: MongoCollection[DBUser])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends UserRepository[F] {

  override def getByUserId(userId: String): OptionT[F, UserT] = {
    findByCriteria(equal("_id", new ObjectId(userId)))
  }

  override def getByEmailAndDeviceType(email: String, deviceType: String) = {
    findByCriteria(and(equal("email", email), equal("deviceType", deviceType)))
  }

  override def create(userRequest: UserT): F[UserT] = {
    val dbUser = DBUser(userRequest)
    Async.fromFuture(M.pure(collection.insertOne(dbUser).toFuture().map(_ => dbUser.toUser())))
  }

  override def update(user: UserT): OptionT[F, UserT] = {
    for {
      _ <- OptionT.liftF(Async.fromFuture(M.pure(collection.replaceOne(equal("_id", new ObjectId(user.id)), DBUser(user)).toFuture())))
      updatedUser <- getByUserId(user.id)
    } yield updatedUser
  }

  private def findByCriteria(criteria: Bson) = {
    OptionT(Async.fromFuture(M.pure(collection.find(criteria).toFuture().map(_.headOption.map(_.toUser())))))
  }

  private def findAllByCriteria(criteria: Bson)(implicit M: Monad[F]) =
    Async.fromFuture(M.pure(collection.find(criteria).toFuture())).map(_.map(_.toUser()))

  override def findTrialExpiredUsers(): F[Seq[UserT]] = {
    findAllByCriteria(and(equal("userType", Trial.value),
      lt("endTrialAt", System.currentTimeMillis())
    ))
  }

  override def findUsersWithNotificationsEnabledAndNotSnoozed(): F[Seq[UserT]] = {
    findAllByCriteria(
      and(
        equal("disableAllAlerts", false),
        lt("snoozeTill", System.currentTimeMillis())
      ))
  }

  override def findPremiumExpiredUsers(): F[Seq[UserT]] = {
    findAllByCriteria(
      and(
        equal("userType", Premium.value),
        lt("nextPaymentAt", System.currentTimeMillis())
      ))
  }
}

case class DBUser(_id: ObjectId, email: String, name: String, deviceType: String, startTrialAt: Long, endTrialAt: Long, userType: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, lastPaymentAt: Long, nextPaymentAt: Long) {
  def toUser(): UserT = {
    this.into[UserT]
      .withFieldComputed(_.id, dbUserSession => dbUserSession._id.toHexString)
      .transform
  }
}

object DBUser {
  def apply(userRequest: UserRequest) : DBUser = {
    userRequest.into[DBUser]
      .withFieldComputed(_._id, _ => new ObjectId())
      .transform
  }
  def apply(user: UserT) : DBUser = {
    user.into[DBUser]
      .withFieldComputed(_._id, _ => new ObjectId(user.id))
      .transform
  }
}