package com.uptech.windalerts.infrastructure.repositories.mongo
import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.core.social.AppleTokenRepository
import com.uptech.windalerts.domain.domain.AppleToken
import com.uptech.windalerts.domain.{SurfsUpError, TokenNotFoundError}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Sorts._

import scala.concurrent.ExecutionContext.Implicits.global


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