package com.uptech.windalerts.users

import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.{AppleCredentials, FacebookCredentialsT}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}

import scala.concurrent.ExecutionContext.Implicits.global

trait AppleCredentialsRepository[F[_]] {
  def create(credentials: AppleCredentials): F[AppleCredentials]

  def count(email: String, deviceType: String): F[Int]

  def findByAppleId(appleId:String): EitherT[F, UserNotFoundError, AppleCredentials]
}

class MongoAppleCredentialsRepositoryAlgebra(collection: MongoCollection[AppleCredentials])(implicit cs: ContextShift[IO]) extends AppleCredentialsRepository[IO] {
  override def create(credentials: AppleCredentials): IO[AppleCredentials] =
    IO.fromFuture(IO(collection.insertOne(credentials).toFuture().map(_ => credentials)))

  override def count(email: String, deviceType: String): IO[Int] =
    findByCriteria(and(equal("email", email), equal("deviceType", deviceType))).map(_.size)

  override def findByAppleId(appleId:String): EitherT[IO, UserNotFoundError, AppleCredentials] =
    EitherT.fromOptionF(IO.fromFuture(IO(collection.find(equal("appleId", appleId)).headOption)), UserNotFoundError())

  private def findByCriteria(criteria: Bson) =
    IO.fromFuture(IO(collection.find(criteria).toFuture()))
}