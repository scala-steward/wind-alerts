package com.uptech.windalerts.users

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.domain.{Credentials}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Updates.set

import scala.concurrent.ExecutionContext.Implicits.global


trait CredentialsRepositoryAlgebra {
  def count(email: String, deviceType: String): IO[Int]

  def create(credentials: Credentials): IO[Credentials]

  def findByCreds(email: String, deviceType: String): OptionT[IO, Credentials]

  def updatePassword(userId: String, password: String): OptionT[IO, Unit]
}

class MongoCredentialsRepository(collection: MongoCollection[Credentials])(implicit cs: ContextShift[IO]) extends CredentialsRepositoryAlgebra {
  override def count(email: String, deviceType: String): IO[Int] =
    findByCriteria(and(equal("email", email), equal("deviceType", deviceType))).map(_.size)

  override def create(credentials: Credentials): IO[Credentials] =
    IO.fromFuture(IO(collection.insertOne(credentials).toFuture().map(_ => credentials)))

  override def findByCreds(email: String, deviceType: String): OptionT[IO, Credentials] =
    OptionT(findByCriteria(and(equal("email", email), equal("deviceType", deviceType))).map(_.headOption))

  private def findByCriteria(criteria: Bson) =
    IO.fromFuture(IO(collection.find(criteria).toFuture()))

  override def updatePassword(userId: String, password: String): OptionT[IO, Unit] =
    OptionT.liftF(IO.fromFuture(IO(collection.updateOne(equal("_id", new ObjectId(userId)), set("password", password)).toFuture().map(_ => ()))))
}
