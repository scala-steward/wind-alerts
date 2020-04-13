package com.uptech.windalerts.users


import cats.data.{EitherT, OptionT}
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.{UserT}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Updates.set

import scala.concurrent.ExecutionContext.Implicits.global



trait UserRepositoryAlgebra[F[_]] {

  def getByUserIdEitherT(userId: String): EitherT[F, Exception, UserT]

  def getByUserId(userId: String): F[Option[UserT]]

  def getByEmailAndDeviceType(email: String, deviceType: String): F[Option[UserT]]

  def create(user: UserT): F[UserT]

  def update(user: UserT): OptionT[F, UserT]

  def updateDeviceToken(userId: String, deviceToken: String): OptionT[F, Unit]

}


class MongoUserRepository(collection: MongoCollection[UserT])(implicit cs: ContextShift[IO]) extends UserRepositoryAlgebra[IO] {
  override def getByUserIdEitherT(userId: String): EitherT[IO, Exception, UserT] = {
    OptionT(getByUserId(userId)).toRight(UserNotFoundError())
  }

  override def getByUserId(userId: String): IO[Option[UserT]] = {
    findByCriteria(equal("_id", new ObjectId(userId)))
  }

  private def findByCriteria(criteria: Bson) = {
    IO.fromFuture(IO(collection.find(criteria).toFuture().map(_.headOption)))
  }

  override def getByEmailAndDeviceType(email: String, deviceType: String): IO[Option[UserT]] = {
    findByCriteria(and(equal("email", email), equal("deviceType", deviceType)))
  }

  override def create(user: UserT): IO[UserT] = {
    IO.fromFuture(IO(collection.insertOne(user).toFuture().map(_ => user)))
  }

  override def update(user: domain.UserT): OptionT[IO, domain.UserT] = {
    OptionT.liftF(
      for {
        updateResultIO <- IO.fromFuture(IO(collection.replaceOne(equal("_id", user._id), user).toFuture().map(_ => user)))
      } yield updateResultIO)
  }

  override def updateDeviceToken(userId: String, deviceToken: String): OptionT[IO, Unit] = {
    OptionT.liftF(IO.fromFuture(IO(collection.updateOne(equal("_id", new ObjectId(userId)), set("deviceToken", deviceToken)).toFuture().map(_ => ()))))
  }
}
