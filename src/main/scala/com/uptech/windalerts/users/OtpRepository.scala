package com.uptech.windalerts.users

import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.domain.OTPWithExpiry
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._

import scala.concurrent.ExecutionContext.Implicits.global

trait OtpRepository {
  def exists(otp: String, userId: String): EitherT[IO, OtpNotFoundError, OTPWithExpiry]

  def create(otp: OTPWithExpiry): IO[OTPWithExpiry]

  def updateForUser(userId:String, otp: OTPWithExpiry): IO[OTPWithExpiry]
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

  override def updateForUser(userId: String, otp: OTPWithExpiry): IO[OTPWithExpiry] = {
    IO.fromFuture(IO(collection.updateOne(equal("userId", otp.userId), Seq(set("otp", otp.otp), set("expiry", otp.expiry))).toFuture().map(_ => otp)))
  }
}
