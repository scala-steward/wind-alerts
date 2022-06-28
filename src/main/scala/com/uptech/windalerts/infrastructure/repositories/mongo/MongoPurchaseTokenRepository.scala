package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.Monad
import cats.data.OptionT
import cats.effect.{Async, ContextShift}
import cats.mtl.Raise
import com.uptech.windalerts.core.TokenNotFoundError
import com.uptech.windalerts.core.social.subscriptions.{PurchaseToken, PurchaseTokenRepository}
import com.uptech.windalerts.infrastructure.Environment.EnvironmentAsk
import io.scalaland.chimney.dsl._
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Sorts.{descending, orderBy, _}

import scala.concurrent.ExecutionContext.Implicits.global
import cats.implicits._

class MongoPurchaseTokenRepository[F[_] : EnvironmentAsk](collectionName: String)(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends PurchaseTokenRepository[F] {
  private val env = implicitly[EnvironmentAsk[F]]

  def getCollection(): F[MongoCollection[DBPurchaseToken]] = {
    MongoRepository.getCollection(collectionName)
  }

  override def create(userId: String,
                      purchaseToken: String,
                      creationTime: Long): F[PurchaseToken] = {
    val dbPurchaseToken = DBPurchaseToken(userId, purchaseToken, creationTime)

    for {
      collection <- getCollection()
      purchaseToken <- Async.fromFuture(M.pure(collection.insertOne(dbPurchaseToken).toFuture().map(_ => dbPurchaseToken.toPurchaseToken())))
    } yield purchaseToken

  }

  override def getLastForUser(userId: String)(implicit FR: Raise[F, TokenNotFoundError]): F[PurchaseToken] = {
    findLastCreationTime(equal("userId", userId))
  }

  override def getByToken(purchaseToken: String)(implicit FR: Raise[F, TokenNotFoundError]): F[PurchaseToken] = {
    findLastCreationTime(equal("purchaseToken", purchaseToken))
  }

  private def findLastCreationTime(criteria: Bson)(implicit FR: Raise[F, TokenNotFoundError]): F[PurchaseToken] = {
    val result = for {
      collection <- getCollection()
      token <- Async.fromFuture(M.pure(collection.find(
        criteria
      ).sort(orderBy(descending("creationTime"))).collect().toFuture().map(_.headOption.map(_.toPurchaseToken()))))
    } yield token


    OptionT(result).getOrElseF(FR.raise(TokenNotFoundError()))
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
            creationTime: Long) = new DBPurchaseToken(new ObjectId(), userId, purchaseToken, creationTime)
}
