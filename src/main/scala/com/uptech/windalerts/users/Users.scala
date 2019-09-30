package com.uptech.windalerts.users

import cats.effect.IO
import com.google.firebase.auth.{FirebaseAuth, FirebaseAuthException, FirebaseToken}
import com.uptech.windalerts.domain.Errors.WindAlertError

import scala.util.{Success, Try}

trait Users extends Serializable {
  val users: Users.Service
}

object Users {
  trait Service {
    def verify(header: String): IO[FirebaseToken]
    def verify1(header: String): IO[Either[Throwable, FirebaseToken]]
  }

  class FireStoreBackedService(auth:FirebaseAuth) extends Service {
    override def verify(header: String): IO[FirebaseToken] = {
      IO(auth.verifyIdToken(header.replaceFirst("Bearer ", "")))
    }

    override def verify1(header: String): IO[Either[Throwable, FirebaseToken]] = {
      IO(Try(auth.verifyIdToken(header.replaceFirst("Bearer ", ""))).toEither)
    }

  }

  object MyExtensions {

    implicit class RichTry[T](t:Try[T]){
      def toEither:Either[Throwable,T] = t.transform(s => Success(Right(s)), f => Success(Left(f))).get
    }
  }
}