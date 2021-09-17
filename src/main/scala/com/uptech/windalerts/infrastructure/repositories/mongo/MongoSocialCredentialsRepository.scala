package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.Monad
import cats.effect.{Async, ContextShift}
import com.uptech.windalerts.core.credentials.SocialCredentialsRepository
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.{and, equal}

import scala.concurrent.ExecutionContext.Implicits.global

class MongoSocialCredentialsRepository[F[_], T : scala.reflect.ClassTag](collection: MongoCollection[T])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F])  extends SocialCredentialsRepository[F, T] {
  override def create(credentials: T): F[T] =
    Async.fromFuture(M.pure(collection.insertOne(credentials).toFuture().map(_ => credentials)))

  override def find(email: String, deviceType: String): F[Option[T]] =
    Async.fromFuture(M.pure(collection.find(and(equal("email", email), equal("deviceType", deviceType))).toFuture().map(_.headOption)))
}