package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.effect.{Async, ContextShift}
import cats.mtl.Raise
import com.uptech.windalerts.core.{RefreshTokenNotFoundError, TokenNotFoundError}
import com.uptech.windalerts.core.refresh.tokens.{UserSession, UserSessionRepository}
import io.scalaland.chimney.dsl._
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.set

import scala.concurrent.ExecutionContext.Implicits.global

class MongoUserSessionRepository[F[_]](collection: MongoCollection[DBUserSession])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends UserSessionRepository[F] {
  override def create(refreshToken: String, expiry: Long, userId: String, deviceToken: String): F[UserSession] = {
    val dBUserSession = DBUserSession(refreshToken, expiry, userId, deviceToken)
    Async.fromFuture(M.pure(collection.insertOne(dBUserSession).toFuture().map(_ => dBUserSession.toUserSession())))
  }

  override def getByRefreshToken(refreshToken: String)(implicit RTNF: Raise[F, RefreshTokenNotFoundError]): F[UserSession] = {
    findByCriteria(equal("refreshToken", refreshToken)).getOrElseF(RTNF.raise(RefreshTokenNotFoundError()))
  }

  override def getByUserId(userId: String): OptionT[F, UserSession] = {
    findByCriteria(equal("userId", userId))
  }

  private def findByCriteria(criteria: Bson) = {
    OptionT(
      Async.fromFuture(
        M.pure(collection.find(criteria)
          .toFuture()
          .map(_.headOption.map(_.toUserSession))
        )
      )
    )
  }

  override def deleteForUserId(userId: String): F[Unit] = {
    Async.fromFuture(M.pure(collection.deleteOne(equal("userId", userId)).toFuture().map(_ => ())))
  }


  override def updateExpiry(id: String, expiry: Long): EitherT[F, TokenNotFoundError, UserSession] = {
    for {
      _ <- EitherT.liftF(Async.fromFuture(M.pure(collection.updateOne(equal("_id", new ObjectId(id)), set("expiry", expiry)).toFuture())))
      updated <- getById(id).toRight(TokenNotFoundError("Token not found"))
    } yield updated
  }

  private def getById(id: String) = {
    findByCriteria(equal("_id", new ObjectId(id)))
  }

  override def updateDeviceToken(userId: String, deviceToken: String): F[Unit] = {
    Async.fromFuture(M.pure(collection.updateOne(equal("userId", userId), set("deviceToken", deviceToken)).toFuture().map(_ => ())))
  }
}


case class DBUserSession(_id: ObjectId, refreshToken: String, expiry: Long, userId: String, deviceToken: String) {
  def toUserSession(): UserSession = {
    this.into[UserSession]
      .withFieldComputed(_.id, dbUserSession => dbUserSession._id.toHexString)
      .transform
  }
}

object DBUserSession {
  def apply(refreshToken: String, expiry: Long, userId: String, deviceToken: String) = new DBUserSession(
    new ObjectId(),
    refreshToken,
    expiry,
    userId,
    deviceToken)

}
