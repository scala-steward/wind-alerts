package com.uptech.windalerts.users
import java.util

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import com.google.cloud.firestore
import com.google.cloud.firestore.{CollectionReference, Firestore}
import com.uptech.windalerts.domain.conversions._
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.User

import scala.beans.BeanProperty

class FirestoreUserRepository(db:Firestore)(implicit cs: ContextShift[IO]) extends UserRepositoryAlgebra {
  private val usersCollection: CollectionReference = db.collection("users")

  override def getById(id: String): IO[Option[User]] = {
    getByQuery(usersCollection.whereEqualTo("userId", id))
  }

  override def create(user: domain.User): IO[domain.User] = {
    for {
      _ <- IO.fromFuture(IO(j2sFuture(usersCollection.add(toBean(user)))))
      alert <- IO(user)
    } yield alert
  }

  override def update(user: domain.User): OptionT[IO, domain.User] = ???

  override def get(userId: String): OptionT[IO, domain.User] = ???

  override def delete(userId: String): OptionT[IO, domain.User] = ???

  override def deleteByUserName(userName: String): OptionT[IO, domain.User] = ???

  private def toBean(user: domain.User) = {
    new UserBean(user.id, user.name, user.deviceId, user.deviceToken)
  }

  private def getByQuery(query: firestore.Query) = {
    for {
      collection <- IO.fromFuture(IO(j2sFuture(query.get())))
      filtered <- IO(
        j2sMap(collection.getDocuments)
          .map(document => {
            val User(user) = (document.get("userId").asInstanceOf[String], j2sm(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
            user
          }))
    } yield filtered.headOption

  }
}


class UserBean(
                @BeanProperty var userId: String,
                @BeanProperty var name: String,
                @BeanProperty var deviceId: String,
                @BeanProperty var deviceToken: String,
              ) {}