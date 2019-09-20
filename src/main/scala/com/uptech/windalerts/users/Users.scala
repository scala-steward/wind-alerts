package com.uptech.windalerts.users

import cats.effect.IO
import com.google.firebase.auth.{FirebaseAuth, FirebaseToken}
import com.uptech.windalerts.domain.Domain

trait Users extends Serializable {
  val users: Users.Service
}

object Users {

  trait Service {
    def verify(header: String): IO[FirebaseToken]
  }

  class FireStoreBackedService(auth:FirebaseAuth) extends Service {
    override def verify(header: String): IO[FirebaseToken] = {
      IO(auth.verifyIdToken(header.replaceFirst("Bearer ", "")))
    }
  }

}