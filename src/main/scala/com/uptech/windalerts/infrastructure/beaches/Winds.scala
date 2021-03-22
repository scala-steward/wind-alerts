package com.uptech.windalerts.infrastructure.beaches

import cats.data.EitherT
import cats.effect.Sync
import cats.{Applicative, Functor, Monad}
import com.softwaremill.sttp._
import com.uptech.windalerts.core.beaches.WindsService
import com.uptech.windalerts.domain.domain.{BeachId, SurfsUpEitherT}
import com.uptech.windalerts.domain.{UnknownError, domain}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, parser}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}
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

class WWBackedWindsService[F[_] : Sync](apiKey: String)(implicit backend: SttpBackend[Id, Nothing]) extends WindsService[F]{
  private val logger = getLogger

  override def get(beachId: BeachId)(implicit F: Functor[F]): SurfsUpEitherT[F, domain.Wind] =
    EitherT.fromEither(getFromWillyWeatther(apiKey, beachId))

  def getFromWillyWeatther(apiKey: String, beachId: BeachId)(implicit F: Monad[F]): Either[UnknownError, domain.Wind] = {
    logger.error(s"Fetching wind status for $beachId")
    val res = for {
      body <- sttp.get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?observational=true").send()
        .body
        .left
        .map(UnknownError(_))
      parsed <- parser.parse(body).left.map(f=>UnknownError(f.message))
      wind <- parsed.hcursor.downField("observational").downField("observations").downField("wind").as[Wind].left.map(f=>UnknownError(f.message))
    } yield  domain.Wind(wind.direction, wind.speed, wind.directionText, if (wind.trend > 0)  "Increasing" else "Decreasing")
    res.orElse(Right(domain.Wind(Double.NaN, Double.NaN, "NA", "NA")))
  }

}

