package com.uptech.windalerts.users

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.domain.RefreshToken
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal

import scala.concurrent.ExecutionContext.Implicits.global

trait RefreshTokenRepositoryAlgebra {

  def getByAccessTokenId(accessTokenId: String): OptionT[IO, RefreshToken]

  def create(refreshToken: RefreshToken): IO[RefreshToken]

  def getByRefreshToken(refreshToken: String): OptionT[IO, RefreshToken]

  def deleteForUserId(uid: String): IO[Unit]
}

class MongoRefreshTokenRepositoryAlgebra(collection: MongoCollection[RefreshToken])(implicit cs: ContextShift[IO]) extends RefreshTokenRepositoryAlgebra {
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
}