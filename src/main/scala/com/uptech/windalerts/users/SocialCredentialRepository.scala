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
