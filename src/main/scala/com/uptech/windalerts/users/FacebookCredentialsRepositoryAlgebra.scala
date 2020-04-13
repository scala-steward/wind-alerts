package com.uptech.windalerts.users

import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.FacebookCredentialsT
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}
import scala.concurrent.ExecutionContext.Implicits.global

trait FacebookCredentialsRepositoryAlgebra[F[_]] {
  def create(credentials: domain.FacebookCredentialsT): F[FacebookCredentialsT]

  def count(email: String, deviceType: String): F[Int]
}

class MongoFacebookCredentialsRepositoryAlgebra(collection: MongoCollection[FacebookCredentialsT])(implicit cs: ContextShift[IO]) extends FacebookCredentialsRepositoryAlgebra[IO] {
  override def create(credentials: FacebookCredentialsT): IO[FacebookCredentialsT] =
    IO.fromFuture(IO(collection.insertOne(credentials).toFuture().map(_ => credentials)))

  override def count(email: String, deviceType: String): IO[Int] =
    findByCriteria(and(equal("email", email), equal("deviceType", deviceType))).map(_.size)

  private def findByCriteria(criteria: Bson) =
    IO.fromFuture(IO(collection.find(criteria).toFuture()))
}