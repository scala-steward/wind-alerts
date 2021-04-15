package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.data.{EitherT, OptionT}
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.core.UserNotFoundError
import com.uptech.windalerts.core.user.UserType.{Premium, Trial}
import com.uptech.windalerts.core.user.{UserRepositoryAlgebra, UserT}
import com.uptech.windalerts.domain.domain
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal, lt}
import org.mongodb.scala.model.Updates.set

import scala.concurrent.ExecutionContext.Implicits.global


class MongoUserRepository(collection: MongoCollection[UserT])(implicit cs: ContextShift[IO]) extends UserRepositoryAlgebra[IO] {
  override def getByUserIdEitherT(userId: String): EitherT[IO, UserNotFoundError, UserT] = {
    getByUserId(userId).toRight(UserNotFoundError())
  }

  override def getByUserId(userId: String): OptionT[IO, UserT] = {
    findByCriteria(equal("_id", new ObjectId(userId)))
  }


  override def getByEmailAndDeviceType(email: String, deviceType: String) = {
    findByCriteria(and(equal("email", email), equal("deviceType", deviceType)))
  }

  override def create(user: UserT): IO[UserT] = {
    IO.fromFuture(IO(collection.insertOne(user).toFuture().map(_ => user)))
  }

  override def update(user: UserT): OptionT[IO, UserT] = {
    for {
      _ <- OptionT.liftF(IO.fromFuture(IO(collection.replaceOne(equal("_id", user._id), user).toFuture())))
      updatedUser <- getByUserId(user._id.toHexString)
    } yield updatedUser
  }

  override def updateDeviceToken(userId: String, deviceToken: String): OptionT[IO, Unit] = {
    OptionT.liftF(IO.fromFuture(IO(collection.updateOne(equal("_id", new ObjectId(userId)), set("deviceToken", deviceToken)).toFuture().map(_ => ()))))
  }

  private def findByCriteria(criteria: Bson) = {
    OptionT(findAllByCriteria(criteria).map(_.headOption))
  }

  private def findAllByCriteria(criteria: Bson) =
    IO.fromFuture(IO(collection.find(criteria).toFuture()))

  override def findTrialExpiredUsers(): IO[Seq[UserT]] = {
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
