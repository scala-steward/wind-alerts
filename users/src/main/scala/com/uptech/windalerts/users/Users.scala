package com.uptech.windalerts.users

import cats.effect.IO
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.auth.{FirebaseAuth, FirebaseToken, SessionCookieOptions}
import com.google.firebase.auth.UserRecord.CreateRequest
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.domain.Domain
import org.http4s.Headers

trait Users extends Serializable {
  val users: Users.Service
}

object Users {

  trait Service {
    def verify(header: String): IO[FirebaseToken]

    def registerUser(email: String, password: String
                    ): IO[Domain.User]
  }

  class FireStoreBackedService(auth:FirebaseAuth) extends Service {
    override def registerUser(email: String, password: String): IO[Domain.User] =
      for {

        token <- IO(auth.verifyIdToken(email))
        user <- IO(Domain.User(email, email, password, "token"))
      } yield user

    override def verify(header: String): IO[FirebaseToken] = {
      IO(auth.verifyIdToken(header.replaceFirst("Bearer ", "")))
    }
  }

}