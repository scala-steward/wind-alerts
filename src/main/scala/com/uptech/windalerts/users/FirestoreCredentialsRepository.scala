package com.uptech.windalerts.users

import java.util

import cats.data.{EitherT, OptionT}
import cats.effect.{ContextShift, IO}
import com.google.cloud.firestore
import com.google.cloud.firestore.{CollectionReference, Firestore}
import com.uptech.windalerts.domain.conversions._
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.{Credentials}

import scala.beans.BeanProperty

class FirestoreCredentialsRepository(db: Firestore)(implicit cs: ContextShift[IO]) extends CredentialsRepositoryAlgebra {
  private val credentialsCollection: CollectionReference = db.collection("credentials")

  override def doesNotExist(credentials: domain.Credentials): EitherT[IO, UserAlreadyExistsError, Unit] = {
    findByCreds(credentials.email, credentials.password, credentials.deviceType).map(_ => UserAlreadyExistsError("", "")).toLeft(())
  }

  override def exists(userId: String): EitherT[IO, UserNotFoundError.type, Unit] = {
    ???
  }

  override def create(credentials: domain.Credentials): IO[domain.Credentials] = {
    for {
      document <- IO.fromFuture(IO(j2sFuture(credentialsCollection.add(toBean(credentials)))))
      alert <- IO(credentials.copy(id = Some(document.getId)))
    } yield alert
  }

  override def update(user: domain.Credentials): OptionT[IO, domain.Credentials] = ???

  override def get(userId: String): OptionT[IO, domain.Credentials] = ???

  override def delete(userId: String): OptionT[IO, domain.Credentials] = ???

  override def findByCreds(email: String, password: String, deviceType: String): OptionT[IO, domain.Credentials] = {
    OptionT(getByQuery(
      credentialsCollection
        .whereEqualTo("email", email)
        .whereEqualTo("password", password)
        .whereEqualTo("deviceType", deviceType)
    ))
  }

  private def toBean(credentials: domain.Credentials) = {
    new CredentialsBean(credentials.email, credentials.password, credentials.deviceType)
  }

  private def getByQuery(query: firestore.Query) = {
    for {
      collection <- IO.fromFuture(IO(j2sFuture(query.get())))
      filtered <- IO(
        j2sMap(collection.getDocuments)
          .map(document => {
            val Credentials(credentials) = (document.getId, j2sm(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
            credentials
          }))
    } yield filtered.headOption

  }

}


class CredentialsBean(
                       @BeanProperty var email: String,
                       @BeanProperty var password: String,
                       @BeanProperty var deviceType: String) {}
