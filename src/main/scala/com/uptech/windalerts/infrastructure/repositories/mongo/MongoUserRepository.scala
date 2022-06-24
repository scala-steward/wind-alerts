package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.Monad
import cats.data.OptionT
import cats.effect.{Async, ContextShift}
import cats.implicits.toFunctorOps
import cats.mtl.Raise
import com.uptech.windalerts.core.UserNotFoundError
import com.uptech.windalerts.core.user.UserType.{Premium, Trial}
import com.uptech.windalerts.core.user.{UserRepository, UserT}
import io.scalaland.chimney.dsl._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal, lt}
import cats.implicits._
import com.uptech.windalerts.infrastructure.Environment.EnvironmentAsk

import scala.concurrent.ExecutionContext.Implicits.global

class MongoUserRepository[F[_] : EnvironmentAsk](implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends UserRepository[F] {
  private val env = implicitly[EnvironmentAsk[F]]

  def getCollection(): F[MongoCollection[DBUser]] = {
    MongoRepository.getCollection("users")
  }

  override def getByUserId(userId: String)(implicit FR: Raise[F, UserNotFoundError]): F[UserT] = {
    getByUserIdOption(userId).getOrElseF(FR.raise(UserNotFoundError()))
  }

  override def getByUserIdOption(userId: String) = {
    findByCriteria(equal("_id", new ObjectId(userId)))
  }

  override def getByEmailAndDeviceType(email: String, deviceType: String)(implicit FR: Raise[F, UserNotFoundError]) = {
    findByCriteria(and(equal("email", email), equal("deviceType", deviceType))).getOrElseF(FR.raise(UserNotFoundError()))
  }

  override def create(userRequest: UserT): F[UserT] = {
    val dbUser = DBUser(userRequest)
    for {
      collection <- getCollection()
      user <- Async.fromFuture(M.pure(collection.insertOne(dbUser).toFuture().map(_ => dbUser.toUser())))
    } yield user
  }

  override def update(user: UserT)(implicit FR: Raise[F, UserNotFoundError]): F[UserT] = {
    for {
      collection <- getCollection()
      _ <- Async.fromFuture(M.pure(collection.replaceOne(equal("_id", new ObjectId(user.id)), DBUser(user)).toFuture()))
      updatedUser <- getByUserId(user.id)
    } yield updatedUser
  }

  private def findByCriteria(criteria: Bson) = {
    OptionT(for {
      collection <- getCollection()
      user <- Async.fromFuture(M.pure(collection.find(criteria).toFuture().map(_.headOption.map(_.toUser()))))
    } yield user)
  }

  private def findAllByCriteria(criteria: Bson)(implicit M: Monad[F]) = {
    for {
      collection <- getCollection()
      all <- Async.fromFuture(M.pure(collection.find(criteria).toFuture())).map(_.map(_.toUser()))
    } yield all
  }

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
  def apply(user: UserT): DBUser = {
    user.into[DBUser]
      .withFieldComputed(_._id, _ => new ObjectId(user.id))
      .transform
  }
}