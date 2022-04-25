package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.Monad
import cats.data.OptionT
import cats.effect.{Async, ContextShift}
import com.uptech.windalerts.core.otp.{OTPWithExpiry, OtpRepository}
import io.scalaland.chimney.dsl._
import org.mongodb.scala.MongoCollection
import org.bson.types.ObjectId
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.UpdateOptions
import org.mongodb.scala.model.Updates.set

import scala.concurrent.ExecutionContext.Implicits.global

class MongoOtpRepository[F[_]](collection: MongoCollection[DBOTPWithExpiry])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends OtpRepository[F] {
  override def findByOtpAndUserId(otp: String, userId: String): OptionT[F, OTPWithExpiry] = {
    OptionT(Async.fromFuture(
      M.pure(
        collection.find(
          and(
            equal("userId", userId),
            equal("otp", otp)
          )
        ).headOption().map(_.map(_.toOTPWithExpiry())))))
  }

  override def updateForUser(userId: String, otp: String, expiry: Long): F[OTPWithExpiry] = {
    Async.fromFuture(
      M.pure(
        collection.updateOne(
          equal("userId", userId),
          Seq(set("otp", otp),
            set("expiry", expiry)),
          UpdateOptions().upsert(true))
          .toFuture()
          .map(updateResult => OTPWithExpiry(updateResult.getUpsertedId.asObjectId().getValue.toHexString, otp, expiry, userId))))
  }


  override def deleteForUser(userId: String): F[Unit] = {
    Async.fromFuture(M.pure(collection.deleteOne(equal("userId", userId)).toFuture().map(_ => ())))
  }


}

case class DBOTPWithExpiry(_id: ObjectId, otp: String, expiry: Long, userId: String) {
  def toOTPWithExpiry(): OTPWithExpiry = {
    this.into[OTPWithExpiry]
      .withFieldComputed(_.id, dbOTPWithExpiry => dbOTPWithExpiry._id.toHexString)
      .transform
  }
}

object DBOTPWithExpiry {
  def apply(otp: String, expiry: Long, userId: String) = new DBOTPWithExpiry(new ObjectId(), otp, expiry, userId)
}
