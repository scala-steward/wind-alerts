package com.uptech.windalerts.infrastructure.beaches

import cats.Applicative
import cats.mtl.Raise
import com.uptech.windalerts.core.{BeachNotFoundError, SurfsUpError, UnknownError}
import io.circe.parser

object WillyWeatherHelper {

  def extractError(json: String) = {
    (for {
      parsed <- parser.parse(json).left.map(f => UnknownError(f.message))
      errorCode <- parsed.hcursor.downField("error").downField("code").as[String].left.map(f => UnknownError(f.message))
    } yield errorCode).map(error => {
      error match {
        case "model-not-found" => BeachNotFoundError("Beach not found")
        case _ => UnknownError(json)
      }
    }
    ).getOrElse(UnknownError(""))
  }

  def leftOnBeachNotFoundError[F[_]:Applicative,T](result: Either[SurfsUpError, T], default: T)(implicit  FR: Raise[F, BeachNotFoundError]):F[T] = {
    if (result.isLeft) {
      result.left.get match {
        case e@BeachNotFoundError(_) => FR.raise(e)
        case _ => Applicative[F].pure(default)
      }
    } else {
      Applicative[F].pure(result.toOption.get)
    }
  }
}
