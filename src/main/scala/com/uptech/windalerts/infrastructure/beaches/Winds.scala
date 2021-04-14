package com.uptech.windalerts.infrastructure.beaches

import cats.data.EitherT
import cats.effect.Sync
import cats.{Functor, Monad}
import com.softwaremill.sttp._
import com.uptech.windalerts.core.beaches.WindsService
import com.uptech.windalerts.domain.domain.{BeachId}
import com.uptech.windalerts.domain.{SurfsUpError, UnknownError, domain}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, parser}
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf
import org.log4s.getLogger


case class Wind(
                 speed: Double,
                 gustSpeed: Double,
                 trend: Double,
                 direction: Double,
                 directionText: String
               )

object Wind {
  implicit val windDecoder: Decoder[Wind] = deriveDecoder[Wind]

  implicit def windEntityDecoder[F[_] : Sync]: EntityDecoder[F, Wind] =
    jsonOf
}

class WWBackedWindsService[F[_] : Sync](apiKey: String)(implicit backend: SttpBackend[Id, Nothing]) extends WindsService[F] {
  private val logger = getLogger

  override def get(beachId: BeachId)(implicit F: Functor[F]): cats.data.EitherT[F, com.uptech.windalerts.domain.SurfsUpError, domain.Wind] =
    EitherT.fromEither(getFromWillyWeatther(apiKey, beachId))

  def getFromWillyWeatther(apiKey: String, beachId: BeachId)(implicit F: Monad[F]): Either[SurfsUpError, domain.Wind] = {
    logger.error(s"Fetching wind status for $beachId")

    val res = for {
      body <- sttp.get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?observational=true").send()
        .body
        .left
        .map(left => {
          WillyWeatherHelper.extractError(left)
        })
      parsed <- parser.parse(body).left.map(f => UnknownError(f.message))
      wind <- parsed.hcursor.downField("observational").downField("observations").downField("wind").as[Wind].left.map(f => UnknownError(f.message))
    } yield domain.Wind(wind.direction, wind.speed, wind.directionText, if (wind.trend > 0) "Increasing" else "Decreasing")

    WillyWeatherHelper.leftOnBeachNotFoundError(res, domain.Wind(Double.NaN, Double.NaN, "NA", "NA"))
  }
}

