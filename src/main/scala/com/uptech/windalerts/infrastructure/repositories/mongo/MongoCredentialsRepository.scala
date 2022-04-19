package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.Monad
import cats.data.OptionT
import cats.effect.{Async, ContextShift}
import com.uptech.windalerts.core.credentials.{Credentials, CredentialsRepository}
import io.scalaland.chimney.dsl._
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Updates.set

import scala.concurrent.ExecutionContext.Implicits.global

class MongoCredentialsRepository[F[_]](collection: MongoCollection[DBCredentials])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends CredentialsRepository[F] {
  override def create(email: String, password: String, deviceType: String): F[Credentials] = {
    val dbCredentials = DBCredentials(email, password, deviceType)
    Async.fromFuture(M.pure(collection.insertOne(dbCredentials).toFuture().map(_ => dbCredentials.toCredentials())))
  }

  override def findByEmailAndDeviceType(email: String, deviceType: String): OptionT[F, Credentials] =
    OptionT(Async.fromFuture(M.pure(collection.find(and(equal("email", email), equal("deviceType", deviceType))).toFuture().map(_.headOption.map(_.toCredentials())))))

  override def updatePassword(userId: String, password: String): F[Unit] =
    Async.fromFuture(M.pure(collection.updateOne(equal("_id", new ObjectId(userId)), set("password", password)).toFuture().map(_ => ())))
}



case class DBCredentials(_id: ObjectId,
                           email: String,
                           password: String,
                           deviceType: String) {
  def toCredentials(): Credentials = {
    this.into[Credentials]
      .withFieldComputed(_.id, dbCredentials => dbCredentials._id.toHexString)
      .transform
  }
}

object DBCredentials {
  def apply(email: String, password: String, deviceType: String): DBCredentials = new DBCredentials(new ObjectId(), email, password, deviceType)

}
