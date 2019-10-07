package com.uptech.windalerts.users
import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import com.google.cloud.firestore.{CollectionReference, Firestore}
import com.uptech.windalerts.domain.conversions.j2sFuture
import com.uptech.windalerts.domain.domain

import scala.beans.BeanProperty

class FirestoreUserRepository(db:Firestore)(implicit cs: ContextShift[IO]) extends UserRepositoryAlgebra {
  private val userssCollection: CollectionReference = db.collection("users")

  override def create(user: domain.User): IO[domain.User] = {
    for {
      _ <- IO.fromFuture(IO(j2sFuture(userssCollection.add(toBean(user)))))
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

}


class UserBean(
                @BeanProperty var userId: String,
                @BeanProperty var name: String,
                @BeanProperty var deviceId: String,
                @BeanProperty var deviceToken: String,
              ) {}