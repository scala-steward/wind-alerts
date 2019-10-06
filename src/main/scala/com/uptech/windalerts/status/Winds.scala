package com.uptech.windalerts.status

import cats.Applicative
import cats.effect.{IO, Sync}
import com.softwaremill.sttp._
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.BeachId
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json, parser}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}



trait Winds extends Serializable {
  val alerts: Winds.Service
}

object Winds {

  trait Service {
    def get(beachId: BeachId): IO[domain.Wind]
  }

  val impl: Service = (beachId: BeachId) => {

    val request = sttp.get(uri"https://api.willyweather.com.au/v2/ZjM0ZmY1Zjc5NDQ3N2IzNjE3MmRmYm/locations/${beachId.id}/weather.json?observational=true")
    implicit val backend = HttpURLConnectionBackend()
    val response = request.send()

    val eitherResponse = response.body.map(s => {
      parser.parse(s).getOrElse(Json.Null).hcursor.downField("observational").downField("observations")
        .downField("wind").as[Wind].map(wind => domain.Wind(wind.direction, wind.speed, wind.directionText))
        .getOrElse(domain.Wind(0.0, 0.0, ""))
    })

    val throwableEither = eitherResponse match {
      case Left(s) => Left(new RuntimeException(s))
      case Right(s) => Right(s)
    }
    IO.fromEither(throwableEither)  }

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

    implicit val windEncoder: Encoder[Wind] = deriveEncoder[Wind]

    implicit def windEntityEncoder[F[_] : Applicative]: EntityEncoder[F, Wind] =
      jsonEncoderOf
  }

}