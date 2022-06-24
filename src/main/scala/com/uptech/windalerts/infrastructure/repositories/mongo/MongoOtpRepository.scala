package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.Monad
import cats.data.OptionT
import cats.effect.{Async, ContextShift}
import cats.mtl.Raise
import com.uptech.windalerts.core.OtpNotFoundError
import com.uptech.windalerts.core.otp.{OTPWithExpiry, OtpRepository}
import com.uptech.windalerts.infrastructure.Environment.EnvironmentAsk
import io.scalaland.chimney.dsl._
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.UpdateOptions
import org.mongodb.scala.model.Updates.set
import cats.implicits._
import scala.concurrent.ExecutionContext.Implicits.global

class MongoOtpRepository[F[_] : EnvironmentAsk](implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends OtpRepository[F] {
  private val env = implicitly[EnvironmentAsk[F]]

  def getCollection(): F[MongoCollection[DBOTPWithExpiry]] = {
    MongoRepository.getCollection("otp")
  }

  override def findByOtpAndUserId(otp: String, userId: String)(implicit FR: Raise[F, OtpNotFoundError]): F[OTPWithExpiry] = {
    OptionT(
      for {
        collection <- getCollection()
        otp <- Async.fromFuture(
          M.pure(
            collection.find(
              and(
                equal("userId", userId),
                equal("otp", otp)
              )
            ).headOption().map(_.map(_.toOTPWithExpiry()))))
      } yield otp).getOrElseF(FR.raise(OtpNotFoundError()))

  }

  override def updateForUser(userId: String, otp: String, expiry: Long): F[OTPWithExpiry] = {
    for {
      collection <- getCollection()
      otp <- Async.fromFuture(
        M.pure(
          collection.updateOne(
            equal("userId", userId),
            Seq(set("otp", otp),
              set("expiry", expiry)),
            UpdateOptions().upsert(true))
            .toFuture()
            .map(_ => {
              OTPWithExpiry(otp, expiry, userId)
            })))
    } yield otp
  }


  override def deleteForUser(userId: String): F[Unit] = {
    for {
      collection <- getCollection()
      deleted <- Async.fromFuture(M.pure(collection.deleteOne(equal("userId", userId)).toFuture().map(_ => ())))
    } yield deleted
  }


}

case class DBOTPWithExpiry(_id: ObjectId, otp: String, expiry: Long, userId: String) {
  def toOTPWithExpiry(): OTPWithExpiry = {
    this.into[OTPWithExpiry]
      .transform
  }
}

object DBOTPWithExpiry {
  def apply(otp: String, expiry: Long, userId: String) = new DBOTPWithExpiry(new ObjectId(), otp, expiry, userId)
}
