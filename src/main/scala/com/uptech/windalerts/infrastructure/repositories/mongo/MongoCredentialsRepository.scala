package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.Monad
import cats.data.OptionT
import cats.effect.{Async, ContextShift, IO}
import com.uptech.windalerts.core.credentials.{Credentials, CredentialsRepository}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Updates.set

import scala.concurrent.ExecutionContext.Implicits.global

class MongoCredentialsRepository[F[_]](collection: MongoCollection[Credentials])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends CredentialsRepository[F] {
  override def create(credentials: Credentials): F[Credentials] =
    Async.fromFuture(M.pure(collection.insertOne(credentials).toFuture().map(_ => credentials)))

  override def findByCredentials(email: String, deviceType: String): OptionT[F, Credentials] =
    OptionT(Async.fromFuture(M.pure(collection.find(and(equal("email", email), equal("deviceType", deviceType))).toFuture().map(_.headOption))))

  override def updatePassword(userId: String, password: String): F[Unit] =
    Async.fromFuture(M.pure(collection.updateOne(equal("_id", new ObjectId(userId)), set("password", password)).toFuture().map(_ => ())))
}
