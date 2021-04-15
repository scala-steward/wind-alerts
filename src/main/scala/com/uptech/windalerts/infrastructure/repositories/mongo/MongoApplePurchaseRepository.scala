package com.uptech.windalerts.infrastructure.repositories.mongo
import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.core.{SurfsUpError, TokenNotFoundError}
import com.uptech.windalerts.core.social.subscriptions.{AppleToken, AppleTokenRepository}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Sorts._

import scala.concurrent.ExecutionContext.Implicits.global


class MongoApplePurchaseRepository(collection: MongoCollection[AppleToken])(implicit cs: ContextShift[IO]) extends AppleTokenRepository[IO] {

  override def create(token: AppleToken): EitherT[IO, SurfsUpError, AppleToken] = {
    EitherT.liftF(IO.fromFuture(IO(collection.insertOne(token).toFuture().map(_ => token))))
  }

  override def getLastForUser(userId: String): EitherT[IO, TokenNotFoundError, AppleToken] = {
    findLastCreationTime(equal("userId", userId))
  }

  override def getPurchaseByToken(purchaseToken: String): EitherT[IO, TokenNotFoundError, AppleToken] = {
    findLastCreationTime(equal("purchaseToken", purchaseToken))
  }

  private def findLastCreationTime(criteria: Bson): EitherT[IO, TokenNotFoundError, AppleToken] = {
    EitherT.fromOptionF(for {
      all <- IO.fromFuture(IO(collection.find(
        criteria
      ).sort(orderBy(descending("creationTime"))).collect().toFuture()))
    } yield all.headOption,
      TokenNotFoundError("Token not found"))
  }
}