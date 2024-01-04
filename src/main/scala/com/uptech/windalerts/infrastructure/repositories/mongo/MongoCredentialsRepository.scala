package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.Monad
import cats.data.OptionT
import cats.effect.{Async, ContextShift}
import cats.implicits._
import com.uptech.windalerts.core.user.credentials.{Credentials, CredentialsRepository}
import com.uptech.windalerts.infrastructure.Environment.EnvironmentAsk
import io.scalaland.chimney.dsl._
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Updates.set

import scala.concurrent.ExecutionContext.Implicits.global


class MongoCredentialsRepository[F[_] : EnvironmentAsk : Monad : Async : ContextShift] extends CredentialsRepository[F] {
  private val env = implicitly[EnvironmentAsk[F]]

  override def create(email: String, password: String, deviceType: String): F[Credentials] = {
    for {
      collection <- getCollection()
      dbCredentials = DBCredentials(email, password, deviceType)
      credentials <-Async.fromFuture(Monad[F].pure(collection.insertOne(dbCredentials).toFuture().map(_ => dbCredentials.toCredentials())))
    } yield credentials
  }

  override def findByEmailAndDeviceType(email: String, deviceType: String): OptionT[F, Credentials] = {
    OptionT(for {
      collection <- getCollection()
      maybeCredentials <- Async.fromFuture (Monad[F].pure(collection.find(and(equal("email", email), equal("deviceType", deviceType))).toFuture().map(_.headOption.map(_.toCredentials()))))
    } yield maybeCredentials)
  }

  override def updatePassword(userId: String, password: String): F[Unit] = {
    for {
      collection <- getCollection()
      _ <- Async.fromFuture(
        Monad[F].pure(collection.updateOne(equal("_id", new ObjectId(userId)), set("password", password)).toFuture().map(_ => ())))
    } yield ()
  }

  def getCollection(): F[MongoCollection[DBCredentials]] = {
    MongoRepository.getCollection("credentials")
  }

  override def deleteByEmailId(emailId: String): F[Unit] = {
    for {
      collection <- getCollection()
      _ <- Async.fromFuture(
        Monad[F].pure(collection.deleteOne(equal("email", emailId)).toFuture().map(_ => ())))
    } yield ()
  }
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
