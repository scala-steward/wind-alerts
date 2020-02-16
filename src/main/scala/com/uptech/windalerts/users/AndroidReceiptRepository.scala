package com.uptech.windalerts.users

import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.domain.AndroidPurchase
import org.mongodb.scala.MongoCollection

import scala.concurrent.ExecutionContext.Implicits.global

trait AndroidPurchaseRepository {
  def create(purchase: AndroidPurchase ): EitherT[IO, ValidationError, AndroidPurchase]
}

class MongoAndroidPurchaseRepository(collection: MongoCollection[AndroidPurchase])(implicit cs: ContextShift[IO]) extends AndroidPurchaseRepository {

  override def create(androidPurchase: AndroidPurchase): EitherT[IO, ValidationError, AndroidPurchase] = {
    EitherT.liftF(IO.fromFuture(IO(collection.insertOne(androidPurchase).toFuture().map(_ => androidPurchase))))
  }

}
