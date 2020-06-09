package com.uptech.windalerts.users

import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.{TokenNotFoundError, SurfsUpError}
import com.uptech.windalerts.domain.domain.AppleToken
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Sorts._

import scala.concurrent.ExecutionContext.Implicits.global

trait AppleTokenRepository[F[_]] {
  def getPurchaseByToken(purchaseToken: String): EitherT[F, SurfsUpError, AppleToken]

  def getLastForUser(userId: String): EitherT[F, SurfsUpError, AppleToken]

  def create(token: AppleToken): EitherT[F, SurfsUpError, AppleToken]
}

class MongoApplePurchaseRepository(collection: MongoCollection[AppleToken])(implicit cs: ContextShift[IO]) extends AppleTokenRepository[IO] {

  override def create(token: AppleToken): EitherT[IO, SurfsUpError, AppleToken] = {
    EitherT.liftF(IO.fromFuture(IO(collection.insertOne(token).toFuture().map(_ => token))))
  }

  override def getLastForUser(userId: String): EitherT[IO, SurfsUpError, AppleToken] = {
    findLastCreationTime(equal("userId", userId))
  }

  override def getPurchaseByToken(purchaseToken: String) = {
    findLastCreationTime(equal("purchaseToken", purchaseToken))
  }

  private def findLastCreationTime(criteria: Bson): EitherT[IO, SurfsUpError, AppleToken] = {
    EitherT.fromOptionF(for {
      all <- IO.fromFuture(IO(collection.find(
        criteria
      ).sort(orderBy(descending("creationTime"))).collect().toFuture()))
    } yield all.headOption,
      TokenNotFoundError())
  }
}