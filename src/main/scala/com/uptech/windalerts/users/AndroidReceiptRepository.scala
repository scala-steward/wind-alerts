package com.uptech.windalerts.users

import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.{TokenNotFoundError, ValidationError}
import com.uptech.windalerts.domain.domain.AndroidToken
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Sorts._

import scala.concurrent.ExecutionContext.Implicits.global

trait AndroidTokenRepository[F[_]]  {
  def getPurchaseByToken(purchaseToken: String) : EitherT[F, ValidationError, AndroidToken]

  def getLastForUser(userId: String): EitherT[F, ValidationError, AndroidToken]

  def create(token: AndroidToken): EitherT[F, ValidationError, AndroidToken]
}

class MongoAndroidPurchaseRepository(collection: MongoCollection[AndroidToken])(implicit cs: ContextShift[IO]) extends AndroidTokenRepository[IO]  {

  override def create(token: AndroidToken): EitherT[IO, ValidationError, AndroidToken] = {
    EitherT.liftF(IO.fromFuture(IO(collection.insertOne(token).toFuture().map(_ => token))))
  }

  override def getLastForUser(userId: String): EitherT[IO, ValidationError, AndroidToken] = {
    EitherT.fromOptionF(for {
      all <- IO.fromFuture(IO(collection.find(
        and(
          equal("userId", userId))
      ).sort(orderBy(descending("creationTime"))).collect().toFuture()))
    } yield all.headOption,
      TokenNotFoundError())
  }

  override def getPurchaseByToken(purchaseToken: String) = {
    EitherT.fromOptionF(for {
      all <- IO.fromFuture(IO(collection.find(
        and(
          equal("purchaseToken", purchaseToken))
      ).sort(orderBy(descending("creationTime"))).collect().toFuture()))
    } yield all.headOption,
      TokenNotFoundError())
  }

}
