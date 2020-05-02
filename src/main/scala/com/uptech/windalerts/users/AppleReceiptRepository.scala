package com.uptech.windalerts.users

import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.domain.{AppleToken}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Sorts._

import scala.concurrent.ExecutionContext.Implicits.global

trait AppleTokenRepository {
  def getPurchaseByToken(purchaseToken: String) : EitherT[IO, ValidationError, AppleToken]

  def getLastForUser(userId: String): EitherT[IO, ValidationError, AppleToken]

  def create(token: AppleToken): EitherT[IO, ValidationError, AppleToken]
}

class MongoApplePurchaseRepository(collection: MongoCollection[AppleToken])(implicit cs: ContextShift[IO]) extends AppleTokenRepository {

  override def create(token: AppleToken): EitherT[IO, ValidationError, AppleToken] = {
    EitherT.liftF(IO.fromFuture(IO(collection.insertOne(token).toFuture().map(_ => token))))
  }

  override def getLastForUser(userId: String): EitherT[IO, ValidationError, AppleToken] = {
    EitherT.fromOptionF(for {
      all <- IO.fromFuture(IO(collection.find(
        and(
          equal("userId", userId))
      ).sort(orderBy(descending("creationTime"))).collect().toFuture()))
    } yield all.headOption,
      TokenNotFoundError())
  }

  override def getPurchaseByToken(purchaseToken: String) = {
    EitherT.fromOptionF(for {
      all <- IO.fromFuture(IO(collection.find(
        and(
          equal("purchaseToken", purchaseToken))
      ).sort(orderBy(descending("creationTime"))).collect().toFuture()))
    } yield all.headOption,
      TokenNotFoundError())
  }

}
