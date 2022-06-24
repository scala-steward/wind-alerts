package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.Monad
import cats.data.OptionT
import cats.effect.{Async, ContextShift}
import cats.mtl.Raise
import com.uptech.windalerts.core.RefreshTokenNotFoundError
import com.uptech.windalerts.core.user.sessions.UserSessionRepository
import com.uptech.windalerts.core.user.sessions.UserSessions.UserSession
import com.uptech.windalerts.infrastructure.Environment.EnvironmentAsk
import io.scalaland.chimney.dsl._
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.set

import scala.concurrent.ExecutionContext.Implicits.global
import cats.implicits._

class MongoUserSessionRepository[F[_] : EnvironmentAsk]()(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends UserSessionRepository[F] {
  private val env = implicitly[EnvironmentAsk[F]]

  def getCollection(): F[MongoCollection[DBUserSession]] = {
    MongoRepository.getCollection("userSessions")
  }

  override def create(refreshToken: String, expiry: Long, userId: String, deviceToken: String): F[UserSession] = {
    val dBUserSession = DBUserSession(refreshToken, expiry, userId, deviceToken)
    for {
      collection <- getCollection()
      session <- Async.fromFuture(M.pure(collection.insertOne(dBUserSession).toFuture().map(_ => dBUserSession.toUserSession())))
    } yield session
  }

  override def getByRefreshToken(refreshToken: String)(implicit RTNF: Raise[F, RefreshTokenNotFoundError]): F[UserSession] = {
    findByCriteria(equal("refreshToken", refreshToken)).getOrElseF(RTNF.raise(RefreshTokenNotFoundError()))
  }

  override def getByUserId(userId: String): OptionT[F, UserSession] = {
    findByCriteria(equal("userId", userId))
  }

  private def findByCriteria(criteria: Bson) = {
    OptionT(
      for {
        collection <- getCollection()
        session <- Async.fromFuture(
          M.pure(collection.find(criteria)
            .toFuture()
            .map(_.headOption.map(_.toUserSession))
          )
        )
      } yield session

    )
  }

  override def deleteForUserId(userId: String): F[Unit] = {
    for {
      collection <- getCollection()
      deleted <- Async.fromFuture(M.pure(collection.deleteOne(equal("userId", userId)).toFuture().map(_ => ())))
    } yield deleted
  }

  override def updateDeviceToken(userId: String, deviceToken: String): F[Unit] = {
    for {
      collection <- getCollection()
      updated <- Async.fromFuture(M.pure(collection.updateOne(equal("userId", userId), set("deviceToken", deviceToken)).toFuture().map(_ => ())))
    } yield updated
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
