package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.Monad
import cats.data.EitherT
import cats.effect.{Async, ContextShift, IO}
import com.uptech.windalerts.core.TokenNotFoundError
import com.uptech.windalerts.core.social.subscriptions.{AndroidToken, AndroidTokenRepository}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Sorts._

import scala.concurrent.ExecutionContext.Implicits.global

class MongoAndroidPurchaseRepository[F[_]](collection: MongoCollection[AndroidToken])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends AndroidTokenRepository[F]  {

  override def create(token: AndroidToken) = {
    EitherT.liftF(Async.fromFuture(M.pure(collection.insertOne(token).toFuture().map(_ => token))))
  }

  override def getLastForUser(userId: String) = {
    findLastByCreationTime(equal("userId", userId))
  }

  override def getPurchaseByToken(purchaseToken: String) = {
    findLastByCreationTime(equal("purchaseToken", purchaseToken))
  }

  private def findLastByCreationTime(criteria: Bson):EitherT[F, TokenNotFoundError, AndroidToken] = {
    EitherT.fromOptionF(
       Async.fromFuture(M.pure(collection.find(criteria).sort(orderBy(descending("creationTime"))).collect().toFuture().map(_.headOption)))
    , TokenNotFoundError("Token not found"))
  }
}
