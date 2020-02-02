package com.uptech.windalerts.users

import java.util

import cats.data.{EitherT, OptionT}
import cats.effect.{ContextShift, IO}
import com.google.cloud.firestore
import com.google.cloud.firestore.{CollectionReference, Firestore, QueryDocumentSnapshot}
import com.uptech.windalerts.domain.conversions._
import com.uptech.windalerts.domain.domain.User
import com.uptech.windalerts.domain.errors.UserNotFound
import com.uptech.windalerts.domain.{FirestoreOps, domain}

import scala.beans.BeanProperty
import scala.concurrent.ExecutionContext.Implicits.global

class FirestoreUserRepository(db: Firestore, dbops:FirestoreOps)(implicit cs: ContextShift[IO]) extends UserRepositoryAlgebra {
  private val usersCollection: CollectionReference = db.collection("users")

  override def create(user: domain.User): IO[domain.User] = {
    for {
      _ <- IO.fromFuture(IO(j2sFuture(usersCollection.document(user.id).create(toBean(user)))))
      saved <- IO(user)
    } yield saved
  }

  override def update(user: domain.User): OptionT[IO, domain.User] = {
    OptionT.liftF(
      for {
        updateResultIO <- IO.fromFuture(IO(j2sFuture(usersCollection.document(user.id).set(toBean(user))).map(r => user)))
      } yield updateResultIO)
  }

  override def delete(userId: String): OptionT[IO, domain.User] = ???

  override def deleteByUserName(userName: String): OptionT[IO, domain.User] = ???

  private def toBean(user: domain.User) = {
    new UserBean(user.email, user.name, user.deviceId, user.deviceToken, user.deviceType, user.startTrialAt, user.userType, user.snoozeTill, user.notificationsPerHour)
  }

  override def getByEmailAndDeviceType(email: String, deviceType: String): IO[Option[User]] = {
    getByQuery(usersCollection.whereEqualTo("email", email).whereEqualTo("deviceType", deviceType))
  }

  def getByUserIdEitherT(userId: String): EitherT[IO, Exception, User] = {
    EitherT.fromOptionF(getByUserId(userId), UserNotFound(userId))
  }


  override def getByUserId(userId: String): IO[Option[User]] = {
    for {
      document <- IO.fromFuture(IO(j2sFuture(usersCollection.document(userId).get())))
      maybeUser <- IO {
        if (document.exists()) {
          val User(user) = (document.getId, j2sm(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
          Some(user)
        } else {
          None
        }
      }
    } yield maybeUser
  }

  private def getByQuery(query: firestore.Query) = {
    val mf: QueryDocumentSnapshot => User = document => {
      val User(user) = (document.getId, j2sm(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
      user
    }
    dbops.getOneByQuery(query, mf)
  }

  override def updateDeviceToken(userId: String, deviceToken: String): OptionT[IO, Unit] = {
    OptionT.liftF(
      for {
        updateResultIO <- IO.fromFuture(IO(j2sFuture(usersCollection.document(userId).update("deviceToken", deviceToken))))
      } yield updateResultIO)
  }
}

class UserBean(
                @BeanProperty var email: String,
                @BeanProperty var name: String,
                @BeanProperty var deviceId: String,
                @BeanProperty var deviceToken: String,
                @BeanProperty var deviceType: String,
                @BeanProperty var startTrialAt: Long,
                @BeanProperty var userType: String,
                @BeanProperty var snoozeTill: Long,
                @BeanProperty var notificationsPerHour: Long
              ) {}