package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.Monad
import cats.data.EitherT
import cats.effect.{Async, ContextShift}
import com.uptech.windalerts.core.social.subscriptions.PurchaseToken
import com.uptech.windalerts.core.{SurfsUpError, TokenNotFoundError}
import com.uptech.windalerts.infrastructure.social.subscriptions.PurchaseTokenRepository
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Sorts._

import scala.concurrent.ExecutionContext.Implicits.global


class MongoPurchaseTokenRepository[F[_]](collection: MongoCollection[PurchaseToken])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends PurchaseTokenRepository[F] {

  override def create(token: PurchaseToken): EitherT[F, SurfsUpError, PurchaseToken] = {
    EitherT.liftF(Async.fromFuture(M.pure(collection.insertOne(token).toFuture().map(_ => token))))
  }

  override def getLastForUser(userId: String): EitherT[F, TokenNotFoundError, PurchaseToken] = {
    findLastCreationTime(equal("userId", userId))
  }

  override def getPurchaseByToken(purchaseToken: String): EitherT[F, TokenNotFoundError, PurchaseToken] = {
    findLastCreationTime(equal("purchaseToken", purchaseToken))
  }

  private def findLastCreationTime(criteria: Bson): EitherT[F, TokenNotFoundError, PurchaseToken] = {
    val result = Async.fromFuture(M.pure(collection.find(
      criteria
    ).sort(orderBy(descending("creationTime"))).collect().toFuture().map(_.headOption)))

    EitherT.fromOptionF(result, TokenNotFoundError("Token not found"))
  }

}