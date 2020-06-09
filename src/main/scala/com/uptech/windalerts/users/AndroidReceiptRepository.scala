package com.uptech.windalerts.users

import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.{TokenNotFoundError, SurfsUpError}
import com.uptech.windalerts.domain.domain.AndroidToken
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Sorts._

import scala.concurrent.ExecutionContext.Implicits.global

trait AndroidTokenRepository[F[_]]  {
  def getPurchaseByToken(purchaseToken: String) : EitherT[F, SurfsUpError, AndroidToken]

  def getLastForUser(userId: String): EitherT[F, SurfsUpError, AndroidToken]

  def create(token: AndroidToken): EitherT[F, SurfsUpError, AndroidToken]
}

class MongoAndroidPurchaseRepository(collection: MongoCollection[AndroidToken])(implicit cs: ContextShift[IO]) extends AndroidTokenRepository[IO]  {

  override def create(token: AndroidToken): EitherT[IO, SurfsUpError, AndroidToken] = {
    EitherT.liftF(IO.fromFuture(IO(collection.insertOne(token).toFuture().map(_ => token))))
  }

  override def getLastForUser(userId: String): EitherT[IO, SurfsUpError, AndroidToken] = {
    findLastByCreationTime(equal("userId", userId))
  }

  override def getPurchaseByToken(purchaseToken: String) = {
    findLastByCreationTime(equal("purchaseToken", purchaseToken))
  }

  private def findLastByCreationTime(criteria: Bson):EitherT[IO, SurfsUpError, AndroidToken] = {
    EitherT.fromOptionF(for {
      all <- IO.fromFuture(IO(collection.find(criteria).sort(orderBy(descending("creationTime"))).collect().toFuture()))
    } yield all.headOption, TokenNotFoundError())
  }
}
