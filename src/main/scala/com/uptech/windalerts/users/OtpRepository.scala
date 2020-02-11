package com.uptech.windalerts.users

import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.domain.OTPWithExpiry
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.{and, equal}

import scala.concurrent.ExecutionContext.Implicits.global

trait OtpRepository {
  def exists(otp: String, userId: String): EitherT[IO, OtpNotFoundError, OTPWithExpiry]

  def create(otp: OTPWithExpiry): IO[OTPWithExpiry]
}

class MongoOtpRepository(collection: MongoCollection[OTPWithExpiry])(implicit cs: ContextShift[IO]) extends OtpRepository {
  def exists(otp: String, userId: String): EitherT[IO, OtpNotFoundError, OTPWithExpiry] = {
    EitherT.fromOptionF(for {
      all <- IO.fromFuture(IO(collection.find(
        and(
          equal("userId", userId),
          equal("otp", otp)
        )
      ).collect().toFuture()))
    } yield all.headOption,
      OtpNotFoundError())
  }

  override def create(otp: OTPWithExpiry): IO[OTPWithExpiry] = {
    IO.fromFuture(IO(collection.insertOne(otp).toFuture().map(_ => otp)))
  }
}
