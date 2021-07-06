package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.Monad
import cats.data.EitherT
import cats.effect.{Async, ContextShift}
import com.uptech.windalerts.core.OtpNotFoundError
import com.uptech.windalerts.core.otp.{OTPWithExpiry, OtpRepository}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.UpdateOptions
import org.mongodb.scala.model.Updates._

import scala.concurrent.ExecutionContext.Implicits.global

class MongoOtpRepository[F[_]](collection: MongoCollection[OTPWithExpiry])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends OtpRepository[F] {
  override def exists(otp: String, userId: String): EitherT[F, OtpNotFoundError, OTPWithExpiry] = {
    EitherT.fromOptionF(
      Async.fromFuture(
        M.pure(
          collection.find(
            and(
              equal("userId", userId),
              equal("otp", otp)
            )
          ).headOption())),
      OtpNotFoundError("OTP not found"))
  }

  override def updateForUser(userId: String, otp: OTPWithExpiry): F[OTPWithExpiry] = {
    Async.fromFuture(
      M.pure(
        collection.updateOne(
          equal("userId", otp.userId),
          Seq(set("otp", otp.otp),
            set("expiry", otp.expiry)),
          UpdateOptions().upsert(true))
          .toFuture()
          .map(_ => otp)))
  }

}
