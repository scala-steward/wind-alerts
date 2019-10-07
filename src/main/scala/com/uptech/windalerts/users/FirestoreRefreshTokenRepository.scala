package com.uptech.windalerts.users

import java.util
import java.util.concurrent.CompletableFuture

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import com.google.cloud.firestore
import com.google.cloud.firestore.{CollectionReference, Firestore}
import com.google.firestore.v1.WriteResult
import com.uptech.windalerts.domain.conversions.{j2sFuture, j2sMap, j2sm}
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.RefreshToken

import scala.beans.BeanProperty

class FirestoreRefreshTokenRepository(db: Firestore)(implicit cs: ContextShift[IO]) extends RefreshTokenRepositoryAlgebra {
  private val tokensCollection: CollectionReference = db.collection("refreshTokens")

  override def create(refreshToken: domain.RefreshToken): IO[domain.RefreshToken] = {
    for {
      _ <- IO.fromFuture(IO(j2sFuture(tokensCollection.add(toBean(refreshToken)))))
      token <- IO(refreshToken)
    } yield token
  }


  override def getByRefreshToken(refreshToken: String): OptionT[IO, domain.RefreshToken] = {
    OptionT(getByQuery(
      tokensCollection
        .whereEqualTo("refreshToken", refreshToken)
    ))
  }
  override def getByAccessTokenId(accessTokenId:String): OptionT[IO, RefreshToken] = {
    OptionT(getByQuery(
      tokensCollection
        .whereEqualTo("accessTokenId", accessTokenId)
    ))
  }


  override def deleteForUserId(userId: String): IO[Unit] = {
    for {
      collection <- IO.fromFuture(IO(j2sFuture(tokensCollection.whereEqualTo("userId", userId).get())))
      deleteResultOption <- IO(
        j2sMap(collection.getDocuments)
          .map(document => {
            db.document(s"refreshTokens/${document.getId}").delete()
          }).headOption.getOrElse(CompletableFuture.completedFuture(WriteResult.getDefaultInstance)))
       deleteResult <- IO.fromFuture(IO(j2sFuture(deleteResultOption)))
    } yield deleteResult
  }

  private def getByQuery(query: firestore.Query) = {
    for {
      collection <- IO.fromFuture(IO(j2sFuture(query.get())))
      filtered <- IO(
        j2sMap(collection.getDocuments)
          .map(document => {
            val RefreshToken(refreshToken) = (document.getId, j2sm(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
            refreshToken
          }))
    } yield filtered.headOption
  }

  private def toBean(refreshToken: domain.RefreshToken) = new RefreshTokenBean(refreshToken.refreshToken, refreshToken.expiry, refreshToken.userId, refreshToken.accessTokenId)

}

class RefreshTokenBean(
                        @BeanProperty var refreshToken: String,
                        @BeanProperty var expiry: Long,
                        @BeanProperty var userId: String,
                        @BeanProperty var accessTokenId: String) {}