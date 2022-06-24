package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.Monad
import cats.effect.{Async, ContextShift}
import com.uptech.windalerts.core.user.credentials.{SocialCredentials, SocialCredentialsRepository}
import io.scalaland.chimney.dsl._
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.{and, equal}

import scala.concurrent.ExecutionContext.Implicits.global
import cats.implicits._
import com.uptech.windalerts.infrastructure.Environment.EnvironmentAsk

class MongoSocialCredentialsRepository[F[_] : EnvironmentAsk](collectionName: String)(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends SocialCredentialsRepository[F] {
  private val env = implicitly[EnvironmentAsk[F]]

  def getCollection(): F[MongoCollection[DBSocialCredentials]] = {
    MongoRepository.getCollection(collectionName)
  }

  override def create(email: String, socialId: String, deviceType: String): F[SocialCredentials] = {
    val dbSocialCredentials = DBSocialCredentials(email, socialId, deviceType)
    for {
      collection <- getCollection()
      credentials <- Async.fromFuture(M.pure(collection.insertOne(dbSocialCredentials).toFuture().map(_ => dbSocialCredentials.toSocialCredentials())))
    } yield credentials
  }

  override def find(email: String, deviceType: String): F[Option[SocialCredentials]] = {
    for {
      collection <- getCollection()
      credentials <- Async.fromFuture(M.pure(collection.find(and(equal("email", email), equal("deviceType", deviceType))).toFuture().map(_.headOption.map(_.toSocialCredentials()))))
    } yield credentials
  }
}

case class DBSocialCredentials(_id: ObjectId,
                               email: String,
                               socialId: String,
                               deviceType: String) {
  def toSocialCredentials(): SocialCredentials = {
    this.into[SocialCredentials]
      .withFieldComputed(_.id, dbCredentials => dbCredentials._id.toHexString)
      .transform
  }
}

object DBSocialCredentials {
  def apply(email: String, socialId: String, deviceType: String): DBSocialCredentials = new DBSocialCredentials(new ObjectId(), email, socialId, deviceType)
}
