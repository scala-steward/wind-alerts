package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.effect.{Async, ContextShift}
import com.uptech.windalerts.core.UserNotFoundError
import com.uptech.windalerts.core.user.UserType.{Premium, Trial}
import com.uptech.windalerts.core.user.{UserRepository, UserT}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal, lt}
import org.mongodb.scala.model.Updates.set

import scala.concurrent.ExecutionContext.Implicits.global


class MongoUserRepository[F[_]](collection: MongoCollection[UserT])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends UserRepository[F] {

  override def getByUserId(userId: String): OptionT[F, UserT] = {
    findByCriteria(equal("_id", new ObjectId(userId)))
  }

  override def getByEmailAndDeviceType(email: String, deviceType: String) = {
    findByCriteria(and(equal("email", email), equal("deviceType", deviceType)))
  }

  override def create(user: UserT): F[UserT] = {
    Async.fromFuture(M.pure(collection.insertOne(user).toFuture().map(_ => user)))
  }

  override def update(user: UserT): OptionT[F, UserT] = {
    for {
      _ <- OptionT.liftF(Async.fromFuture(M.pure(collection.replaceOne(equal("_id", user._id), user).toFuture())))
      updatedUser <- getByUserId(user._id.toHexString)
    } yield updatedUser
  }

  private def findByCriteria(criteria: Bson) = {
    OptionT(Async.fromFuture(M.pure(collection.find(criteria).toFuture().map(_.headOption))))
  }

  private def findAllByCriteria(criteria: Bson) =
    Async.fromFuture(M.pure(collection.find(criteria).toFuture()))

  override def findTrialExpiredUsers(): F[Seq[UserT]] = {
    findAllByCriteria(and(equal("userType", Trial.value),
      lt("endTrialAt", System.currentTimeMillis())
    ))
  }

  override def findAndroidPremiumExpiredUsers() = {
    findAllByCriteria(
      and(equal("userType", Premium.value),
        equal("deviceType", "ANDROID"),
        lt("nextPaymentAt", System.currentTimeMillis())
      ))
  }

  override def findApplePremiumExpiredUsers() = {
    findAllByCriteria(
      and(
        equal("userType", Premium.value),
        equal("deviceType", "IOS"),
        lt("nextPaymentAt", System.currentTimeMillis())
      ))
  }
}
