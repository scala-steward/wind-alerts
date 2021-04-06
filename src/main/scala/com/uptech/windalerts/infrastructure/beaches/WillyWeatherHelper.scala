package com.uptech.windalerts.infrastructure.beaches

import com.uptech.windalerts.domain.{BeachNotFoundError, SurfsUpError, UnknownError, domain}
import io.circe.parser

object WillyWeatherHelper {

  def extractError(json: String) = {
    (for {
      parsed <- parser.parse(json).left.map(f => UnknownError(f.message))
      errorCode <- parsed.hcursor.downField("error").downField("code").as[String].left.map(f => UnknownError(f.message))
    } yield errorCode).map(error => {
      error match {
        case "model-not-found" => BeachNotFoundError()
        case _ => UnknownError(json)
      }
    }
    ).getOrElse(UnknownError(""))
  }

  def leftOnBeachNotFoundError[T](result: Either[SurfsUpError, T], default: T) = {
    if (result.isLeft) {
      result.left.get match {
        case e@BeachNotFoundError() => Left(e)
        case _ => Right(default)
      }
    } else {
      result
    }
  }
}
