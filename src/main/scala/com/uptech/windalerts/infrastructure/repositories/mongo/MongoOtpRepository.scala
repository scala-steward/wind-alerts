package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.OtpNotFoundError
import com.uptech.windalerts.domain.domain.OTPWithExpiry
import com.uptech.windalerts.users.OtpRepository
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.UpdateOptions
import org.mongodb.scala.model.Updates._
import scala.concurrent.ExecutionContext.Implicits.global

class MongoOtpRepository(collection: MongoCollection[OTPWithExpiry])(implicit cs: ContextShift[IO]) extends OtpRepository[IO] {
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

  override def updateForUser(userId: String, otp: OTPWithExpiry): IO[OTPWithExpiry] = {
    IO.fromFuture(IO(collection.updateOne(equal("userId", otp.userId), Seq(set("otp", otp.otp), set("expiry", otp.expiry)), UpdateOptions().upsert(true)).toFuture().map(_ => otp)))
  }
}
