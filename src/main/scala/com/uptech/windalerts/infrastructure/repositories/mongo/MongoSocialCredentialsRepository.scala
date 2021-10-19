package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.Monad
import cats.effect.{Async, ContextShift}
import com.uptech.windalerts.core.credentials.{SocialCredentials, SocialCredentialsRepository}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.{and, equal}

import scala.concurrent.ExecutionContext.Implicits.global

class MongoSocialCredentialsRepository[F[_]](collection: MongoCollection[SocialCredentials])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F])  extends SocialCredentialsRepository[F] {
  override def create(credentials: SocialCredentials): F[SocialCredentials] =
    Async.fromFuture(M.pure(collection.insertOne(credentials).toFuture().map(_ => credentials)))

  override def find(email: String, deviceType: String): F[Option[SocialCredentials]] =
    Async.fromFuture(M.pure(collection.find(and(equal("email", email), equal("deviceType", deviceType))).toFuture().map(_.headOption)))
}