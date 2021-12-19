package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.Monad
import cats.data.EitherT
import cats.effect.{Async, ContextShift}
import com.uptech.windalerts.core.social.subscriptions.{PurchaseToken, PurchaseTokenRepository}
import com.uptech.windalerts.core.{SurfsUpError, TokenNotFoundError}
import io.scalaland.chimney.dsl._
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Sorts._

import scala.concurrent.ExecutionContext.Implicits.global

class MongoPurchaseTokenRepository[F[_]](collection: MongoCollection[DBPurchaseToken])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends PurchaseTokenRepository[F] {

  override def create(userId: String,
                      purchaseToken: String,
                      creationTime: Long): EitherT[F, SurfsUpError, PurchaseToken] = {
    val dbPurchaseToken = DBPurchaseToken(userId, purchaseToken, creationTime)
    EitherT.liftF(Async.fromFuture(M.pure(collection.insertOne(dbPurchaseToken).toFuture().map(_ => dbPurchaseToken.toPurchaseToken()))))
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
    ).sort(orderBy(descending("creationTime"))).collect().toFuture().map(_.headOption.map(_.toPurchaseToken()))))

    EitherT.fromOptionF(result, TokenNotFoundError("Token not found"))
  }

}


case class DBPurchaseToken(_id: ObjectId,
                           userId: String,
                           purchaseToken: String,
                           creationTime: Long) {
  def toPurchaseToken(): PurchaseToken = {
    this.into[PurchaseToken]
      .withFieldComputed(_.id, dbPurchaseToken => dbPurchaseToken._id.toHexString)
      .transform
  }
}

object DBPurchaseToken {
    def apply(userId: String,
              purchaseToken: String,
              creationTime: Long) = new DBPurchaseToken(new ObjectId(), userId,  purchaseToken, creationTime)
}
