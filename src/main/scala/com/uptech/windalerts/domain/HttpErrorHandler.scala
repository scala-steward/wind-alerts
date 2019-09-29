package com.uptech.windalerts.domain


import java.lang.RuntimeException

import cats.Monad
import com.google.firebase.auth.FirebaseAuthException
import org.http4s.{Response, Status}
import org.http4s.dsl.Http4sDsl

class HttpErrorHandler[F[_] : Monad] extends Http4sDsl[F] {
  val handleThrowable: Throwable => F[Response[F]] = {

    case e: FirebaseAuthException => BadRequest(e.getErrorCode)
    case e: Errors.OperationNotPermitted => Forbidden(e.message)
    case e: Errors.RecordNotFound => NotFound(e.message)
    case e: Errors.HeaderNotPresent => BadRequest(e.message)

    case everythingElse => InternalServerError(everythingElse.toString)

  }


}
