package com.uptech.windalerts.users

import cats.data.{EitherT, OptionT}
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.{TokenNotFoundError, ValidationError, domain}
import com.uptech.windalerts.domain.domain.RefreshToken
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.set

import scala.concurrent.ExecutionContext.Implicits.global

trait RefreshTokenRepositoryAlgebra[F[_]] {
  def getByAccessTokenId(accessTokenId: String): OptionT[F, RefreshToken]

  def create(refreshToken: RefreshToken): F[RefreshToken]

  def getByRefreshToken(refreshToken: String): OptionT[F, RefreshToken]

  def deleteForUserId(uid: String): F[Unit]

  def updateExpiry(id: String, expiry: Long): EitherT[F, TokenNotFoundError, RefreshToken]
}

class MongoRefreshTokenRepositoryAlgebra(collection: MongoCollection[RefreshToken])(implicit cs: ContextShift[IO]) extends RefreshTokenRepositoryAlgebra[IO] {
  override def getByAccessTokenId(accessTokenId: String): OptionT[IO, RefreshToken] = {
    findByCriteria(equal("accessTokenId", accessTokenId))
  }

  override def create(refreshToken: RefreshToken): IO[RefreshToken] = {
    IO.fromFuture(IO(collection.insertOne(refreshToken).toFuture().map(_ => refreshToken)))
  }

  override def getByRefreshToken(refreshToken: String): OptionT[IO, RefreshToken] = {
    findByCriteria(equal("refreshToken", refreshToken))
  }

  private def findByCriteria(criteria: Bson) = {
    OptionT(
      IO.fromFuture(
        IO(collection.find(criteria)
          .toFuture()
          .map(_.headOption)
        )
      )
    )
  }

  override def deleteForUserId(userId: String): IO[Unit] = {
    IO.fromFuture(IO(collection.deleteOne(equal("userId", userId)).toFuture().map(_ => ())))
  }


  override def updateExpiry(id: String, expiry: Long): EitherT[IO, TokenNotFoundError, RefreshToken] = {
    for {
      _ <- EitherT.liftF(IO.fromFuture(IO(collection.updateOne(equal("_id", new ObjectId(id)), set("expiry", expiry)).toFuture())))
      updated <- getById(id).toRight(TokenNotFoundError())
    } yield updated
  }

  private def getById(id: String) = {
    findByCriteria(equal("_id", new ObjectId(id)))
  }

}