package com.uptech.windalerts.users

import java.util

import cats.effect.{ContextShift, IO}
import com.google.cloud.firestore
import com.google.cloud.firestore.{CollectionReference, Firestore, WriteResult}
import com.uptech.windalerts.domain.domain.User

import scala.concurrent.ExecutionContext.Implicits.global
import scala.beans.BeanProperty
import scala.collection.JavaConverters
import scala.concurrent.Future

trait UsersRepository extends Serializable {
  val users: UsersRepository.Repository
}


object UsersRepository {

  trait Repository {
    def getById(id: String): IO[Option[User]]
  }

  class FirestoreBackedRepository(db: Firestore)(implicit cs: ContextShift[IO]) extends Repository {
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

    def j2s[A](inputList: util.List[A]) = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

    def j2s[K, V](map: util.Map[K, V]) = JavaConverters.mapAsScalaMap(map).toMap

    def j2s[A](javaFuture: util.concurrent.Future[A]): Future[A] = {
      Future(javaFuture.get())
    }

  }


  class UserBean(
                  @BeanProperty var email: String,
                  @BeanProperty var password: String,
                  @BeanProperty var deviceType: String
                ) {}


}



