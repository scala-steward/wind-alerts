package com.uptech.windalerts.infrastructure.repositories.mongo
import cats.Monad
import cats.data.EitherT
import cats.effect.{Async, ContextShift, IO}
import com.uptech.windalerts.core.{SurfsUpError, TokenNotFoundError}
import com.uptech.windalerts.core.social.subscriptions.{AppleToken, AppleTokenRepository}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Sorts._

import scala.concurrent.ExecutionContext.Implicits.global


class MongoApplePurchaseRepository[F[_]](collection: MongoCollection[AppleToken])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends AppleTokenRepository[F] {

  override def create(token: AppleToken): EitherT[F, SurfsUpError, AppleToken] = {
    EitherT.liftF(Async.fromFuture(M.pure(collection.insertOne(token).toFuture().map(_ => token))))
  }

  override def getLastForUser(userId: String): EitherT[F, TokenNotFoundError, AppleToken] = {
    findLastCreationTime(equal("userId", userId))
  }

  override def getPurchaseByToken(purchaseToken: String): EitherT[F, TokenNotFoundError, AppleToken] = {
    findLastCreationTime(equal("purchaseToken", purchaseToken))
  }

  private def findLastCreationTime(criteria: Bson): EitherT[F, TokenNotFoundError, AppleToken] = {
    val result = Async.fromFuture(M.pure(collection.find(
      criteria
    ).sort(orderBy(descending("creationTime"))).collect().toFuture().map(_.headOption)))

    EitherT.fromOptionF(result, TokenNotFoundError("Token not found"))
  }

}