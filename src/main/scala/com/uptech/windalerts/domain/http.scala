package com.uptech.windalerts.domain

import cats.data.EitherT
import cats.effect.Effect
import cats.implicits._
import com.uptech.windalerts.domain.domain.SurfsUpEitherT
import org.http4s.EntityEncoder
import org.http4s.dsl.Http4sDsl

class http[F[_] : Effect](httpErrorHandler: HttpErrorHandler[F]) extends Http4sDsl[F] {

  def handle[Req, Res](req:F[Req], handler : Req => SurfsUpEitherT[F, Res])(implicit encoder:EntityEncoder[F, Res])  = {
    val action:SurfsUpEitherT[F, Res] = for {
      liftedRequest <- EitherT.liftF(req)
      response <- handler(liftedRequest)
    } yield response
    action.value.flatMap {
      case Right(res) => Ok(res)
      case Left(error) => httpErrorHandler.handleThrowable(error)
    }
  }

  def handle[Req](req:F[Req], handler : Req => SurfsUpEitherT[F, Unit])  = {
    val action:SurfsUpEitherT[F, Unit] = for {
      liftedRequest <- EitherT.liftF(req)
      response <- handler(liftedRequest)
    } yield response
    action.value.flatMap {
      case Right(_) => Ok()
      case Left(error) => httpErrorHandler.handleThrowable(error)
    }
  }

  def handle[Res](handler : () => SurfsUpEitherT[F, Res])(implicit encoder:EntityEncoder[F, Res])  = {
    val action:SurfsUpEitherT[F, Res] = for {
      response <- handler()
    } yield response
    action.value.flatMap {
      case Right(res) => Ok(res)
      case Left(error) => httpErrorHandler.handleThrowable(error)
    }
  }
}
