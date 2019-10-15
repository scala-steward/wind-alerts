package com.uptech.windalerts.users

import java.util

import cats.data.{EitherT, OptionT}
import cats.effect.{ContextShift, IO}
import com.google.cloud.firestore
import com.google.cloud.firestore.{CollectionReference, Firestore}
import com.uptech.windalerts.domain.conversions.{j2sFuture, j2sMap, j2sm}
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.{Credentials, FacebookCredentials}

import scala.beans.BeanProperty

trait FacebookCredentialsRepositoryAlgebra {
  def create(credentials: domain.FacebookCredentials): IO[FacebookCredentials]

  def doesNotExist(email: String, deviceType: String): EitherT[IO, UserAlreadyExistsError, Unit]
}

class FirestoreFacebookCredentialsRepositoryAlgebra(db: Firestore)(implicit cs: ContextShift[IO]) extends FacebookCredentialsRepositoryAlgebra {
  private val credentialsCollection: CollectionReference = db.collection("facebookCredentials")

  override def create(credentials: domain.FacebookCredentials): IO[FacebookCredentials] = {
    for {
      document <- IO.fromFuture(IO(j2sFuture(credentialsCollection.add(toBean(credentials)))))
      saved <- IO(credentials.copy(id = Some(document.getId)))
    } yield saved
  }

  override def doesNotExist(email: String, deviceType: String): EitherT[IO, UserAlreadyExistsError, Unit] = {
    OptionT(getByQuery(
      credentialsCollection
        .whereEqualTo("email", email)
        .whereEqualTo("deviceType", deviceType)
    )).map(_ => UserAlreadyExistsError("", "")).toLeft(())
  }


  private def getByQuery(query: firestore.Query) = {
    for {
      collection <- IO.fromFuture(IO(j2sFuture(query.get())))
      filtered <- IO(
        j2sMap(collection.getDocuments)
          .map(document => {
            val FacebookCredentials(credentials) = (document.getId, j2sm(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
            credentials
          }))
    } yield filtered.headOption

  }


  private def toBean(credentials: domain.FacebookCredentials) = {
    new FacebookCredentialsBean(credentials.email, credentials.accessToken, credentials.deviceType)
  }

}


class FacebookCredentialsBean(
                               @BeanProperty var email: String,
                               @BeanProperty var accessToken: String,
                               @BeanProperty var deviceType: String) {}