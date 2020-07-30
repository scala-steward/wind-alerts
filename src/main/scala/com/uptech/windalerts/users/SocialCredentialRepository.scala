package com.uptech.windalerts.users

import cats.effect.{ContextShift, IO}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}

import scala.concurrent.ExecutionContext.Implicits.global

trait SocialCredentialsRepository[F[_], T] {
  def create(credentials: T): F[T]

  def count(email: String, deviceType: String): F[Int]

  def find(email: String, deviceType: String): F[Option[T]]
}

class MongoSocialCredentialsRepository[T : scala.reflect.ClassTag](collection: MongoCollection[T])(implicit cs: ContextShift[IO]) extends SocialCredentialsRepository[IO, T] {
  override def create(credentials: T): IO[T] =
    IO.fromFuture(IO(collection.insertOne(credentials).toFuture().map(_ => credentials)))

  override def count(email: String, deviceType: String): IO[Int] =
    findByCriteria(and(equal("email", email), equal("deviceType", deviceType))).map(_.size)

  private def findByCriteria(criteria: Bson) =
    IO.fromFuture(IO(collection.find(criteria).toFuture()))

  override def find(email: String, deviceType: String): IO[Option[T]] =
    findByCriteria(and(equal("email", email), equal("deviceType", deviceType))).map(_.headOption)
}