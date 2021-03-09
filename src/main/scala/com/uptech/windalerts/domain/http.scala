package com.uptech.windalerts.domain

import cats.data.{EitherT, OptionT}
import cats.effect.Effect
import cats.implicits._
import domain.{SurfsUpEitherT, UserId}
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRequest, EntityDecoder, EntityEncoder, Response}

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

  def handleCreated[Req, Res](authReq:AuthedRequest[F, UserId], user:UserId, handler : (UserId, Req) => SurfsUpEitherT[F, Res])(implicit decoder:EntityDecoder[F, Req],  encoder:EntityEncoder[F, Res])  = {
    handle(authReq, user, handler, (r:Res) => Created(r))(decoder, encoder)
  }

  def handleNoContentNoDecode[Res](user:UserId, handler : (UserId) => SurfsUpEitherT[F, Res])(implicit encoder:EntityEncoder[F, Res])  = {
    handleNoDecode( user, handler, (r:Res) => NoContent())(encoder)
  }

  def handleOkNoDecode[Res](user:UserId, handler : (UserId) => SurfsUpEitherT[F, Res])(implicit encoder:EntityEncoder[F, Res])  = {
    handleNoDecode( user, handler, (r:Res) => Ok(r))(encoder)
  }

  def handleNoDecode[Res](user:UserId, handler : (UserId) => SurfsUpEitherT[F, Res],  res : Res => F[Response[F]])(implicit encoder:EntityEncoder[F, Res])  = {
    val action = for {
      sent <- handler(user)
    } yield sent
    val response = action.value.flatMap {
      case Right(response) => res(response)
      case Left(error) => httpErrorHandler.handleError(error)
    }
    OptionT.liftF(response)
  }

  def handleOk[Req, Res](authReq:AuthedRequest[F, UserId], user:UserId, handler : (UserId, Req) => SurfsUpEitherT[F, Res])(implicit decoder:EntityDecoder[F, Req],  encoder:EntityEncoder[F, Res])  = {
    handle(authReq, user, handler, (r:Res) => Ok(r))(decoder, encoder)
  }

  def handleEmptyOk[Req, Res](authReq:AuthedRequest[F, UserId], user:UserId, handler : (UserId, Req) => SurfsUpEitherT[F, Res])(implicit decoder:EntityDecoder[F, Req])  = {
    handle(authReq, user, handler, (r:Res) => Ok())(decoder, null)
  }

  def handleNoContent[Req, Res](authReq:AuthedRequest[F, UserId], user:UserId, handler : (UserId, Req) => SurfsUpEitherT[F, Res])(implicit decoder:EntityDecoder[F, Req],  encoder:EntityEncoder[F, Res])  = {
    handle(authReq, user, handler, (r:Res) => NoContent())(decoder, encoder)
  }

  def handle[Req, Res](authReq:AuthedRequest[F, UserId], user:UserId, handler : (UserId, Req) => SurfsUpEitherT[F, Res], res : Res => F[Response[F]])(implicit decoder:EntityDecoder[F, Req],  encoder:EntityEncoder[F, Res])  = {
    val response = authReq.req.decode[Req] { req =>
      val action = for {
        response <- handler(user, req)
      } yield response
      action.value.flatMap {
        case Right(response) => res(response)
        case Left(error) => httpErrorHandler.handleError(error)
      }
    }
    OptionT.liftF(response)
  }
}
