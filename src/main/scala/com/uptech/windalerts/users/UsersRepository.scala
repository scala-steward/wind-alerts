package com.uptech.windalerts.users

import java.util

import cats.effect.IO
import com.google.cloud.firestore
import com.google.cloud.firestore.{CollectionReference, Firestore, WriteResult}
import com.uptech.windalerts.domain.Domain.{User}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.beans.BeanProperty
import scala.collection.JavaConverters
import scala.concurrent.Future

trait UserssRepository extends Serializable {
  val users: UsersRepository.Repository
}


object UsersRepository {

  trait Repository {
    def getById(id: String): IO[Option[User]]
  }

  class FirestoreBackedRepository(db: Firestore) extends Repository {
    private val users: CollectionReference = db.collection("users")

    override def getById(id: String): IO[Option[User]] = {
      getByQuery(users.whereEqualTo("userId", id))
    }


    private def getByQuery(query: firestore.Query) = {
      for {
        collection <- IO.fromFuture(IO(j2s(query.get())))
        filtered <- IO(
          j2s(collection.getDocuments)
            .map(document => {
              val User(user) = (document.get("userId").asInstanceOf[String], j2s(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
              user
            }))
      } yield filtered.headOption

    }

    def toBean(user: User): UserBean = {
      new UserBean(
        user.id,
        user.email,
        user.name,
        user.deviceId,
        user.deviceToken,
        user.deviceType
      )
    }

    def j2s[A](inputList: util.List[A]) = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

    def j2s[K, V](map: util.Map[K, V]) = JavaConverters.mapAsScalaMap(map).toMap

    def j2s[A](javaFuture: util.concurrent.Future[A]): Future[A] = {
      Future(javaFuture.get())
    }

  }

  class UserBean(
                  @BeanProperty var id: String,
                  @BeanProperty var email: String,
                  @BeanProperty var name: String,
                  @BeanProperty var deviceId: String,
                  @BeanProperty var deviceToken: String,
                  @BeanProperty var deviceType: String,
                ) {}


}



