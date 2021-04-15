package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.core.TokenNotFoundError
import com.uptech.windalerts.core.social.subscriptions.{AndroidToken, AndroidTokenRepository}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Sorts._

import scala.concurrent.ExecutionContext.Implicits.global

class MongoAndroidPurchaseRepository(collection: MongoCollection[AndroidToken])(implicit cs: ContextShift[IO]) extends AndroidTokenRepository[IO]  {

  override def create(token: AndroidToken) = {
    EitherT.liftF(IO.fromFuture(IO(collection.insertOne(token).toFuture().map(_ => token))))
  }

  override def getLastForUser(userId: String) = {
    findLastByCreationTime(equal("userId", userId))
  }

  override def getPurchaseByToken(purchaseToken: String) = {
    findLastByCreationTime(equal("purchaseToken", purchaseToken))
  }

  private def findLastByCreationTime(criteria: Bson):EitherT[IO, TokenNotFoundError, AndroidToken] = {
    EitherT.fromOptionF(for {
      all <- IO.fromFuture(IO(collection.find(criteria).sort(orderBy(descending("creationTime"))).collect().toFuture()))
    } yield all.headOption, TokenNotFoundError("Token not found"))
  }
}
