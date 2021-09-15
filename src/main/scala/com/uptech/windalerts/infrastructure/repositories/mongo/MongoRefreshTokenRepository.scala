package com.uptech.windalerts.infrastructure.repositories.mongo
import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.effect.{Async, ContextShift}
import com.uptech.windalerts.core.TokenNotFoundError
import com.uptech.windalerts.core.refresh.tokens.{RefreshToken, RefreshTokenRepository}
import com.uptech.windalerts.core.user.UserId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.set

import scala.concurrent.ExecutionContext.Implicits.global


class MongoRefreshTokenRepository[F[_]](collection: MongoCollection[RefreshToken])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends RefreshTokenRepository[F] {
  override def getByAccessTokenId(accessTokenId: String): OptionT[F, RefreshToken] = {
    findByCriteria(equal("accessTokenId", accessTokenId))
  }

  override def create(refreshToken: RefreshToken): F[RefreshToken] = {
    Async.fromFuture(M.pure(collection.insertOne(refreshToken).toFuture().map(_ => refreshToken)))
  }

  override def getByRefreshToken(refreshToken: String): OptionT[F, RefreshToken] = {
    findByCriteria(equal("refreshToken", refreshToken))
  }

  private def findByCriteria(criteria: Bson) = {
    OptionT(
      Async.fromFuture(
        M.pure(collection.find(criteria)
          .toFuture()
          .map(_.headOption)
        )
      )
    )
  }

  override def deleteForUserId(userId: String): F[Unit] = {
    Async.fromFuture(M.pure(collection.deleteOne(equal("userId", userId)).toFuture().map(_ => ())))
  }


  override def updateExpiry(id: String, expiry: Long): EitherT[F, TokenNotFoundError, RefreshToken] = {
    for {
      _ <- EitherT.liftF(Async.fromFuture(M.pure(collection.updateOne(equal("_id", new ObjectId(id)), set("expiry", expiry)).toFuture())))
      updated <- getById(id).toRight(TokenNotFoundError("Token not found"))
    } yield updated
  }

  private def getById(id: String) = {
    findByCriteria(equal("_id", new ObjectId(id)))
  }

  override def updateAccessTokenId(userId: UserId, newAccessTokenId: String): F[Unit] = {
    (Async.fromFuture(M.pure(collection.updateOne(equal("userId", userId.id), set("accessTokenId", newAccessTokenId)).toFuture().map(_=>()))))

  }
}