package com.uptech.windalerts.status

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone

import cats.Applicative
import cats.effect.{IO, Sync}
import com.softwaremill.sttp._
import com.uptech.windalerts.domain.Domain
import com.uptech.windalerts.domain.Domain.BeachId
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json, parser}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}


trait Swells extends Serializable {
  val alerts: Swells.Service
}

object Swells {

  trait Service {
    def get(beachId: BeachId): IO[Domain.Swell]
  }

  val impl: Service = (beachId: BeachId) => {

    val sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    val request = sttp.get(uri"https://api.willyweather.com.au/v2/ZjM0ZmY1Zjc5NDQ3N2IzNjE3MmRmYm/locations/${beachId.id}/weather.json?forecasts=swell&days=1")
    implicit val backend = HttpURLConnectionBackend()
    val response = request.send()

    val eitherResponse = response.body.map(s => {
      val timeZoneStr = parser.parse(s).getOrElse(Json.Null).hcursor.downField("location").downField("timeZone").as[String]
      val timeZone = TimeZone.getTimeZone(timeZoneStr.getOrElse("Australia/Sydney"))
      parser.parse(s).getOrElse(Json.Null).hcursor.downField("forecasts").downField("swell")
        .downField("days").focus
        .get
        .hcursor
        .downArray
        .downField("entries")
        .values
        .get.flatMap(j => j.as[Swell].toSeq).filter(s => {
        val entry = LocalDateTime.parse(s.dateTime, sdf).atZone(timeZone.toZoneId).withZoneSameInstant(TimeZone.getDefault.toZoneId)

        val currentTime = LocalDateTime.now()

        entry.getHour == currentTime.getHour
      })
        .head
    }
    ).map(swell => Domain.Swell(swell.height, swell.direction, swell.directionText))

    val throwableEither = eitherResponse match {
      case Left(s) => Left(new RuntimeException(s))
      case Right(s) => Right(s)
    }
    IO.fromEither(throwableEither)
  }

  case class Swell(
                    dateTime: String,
                    direction: Double,
                    directionText: String,
                    height: Double,
                    period: Double
                  )

  object Swell {
    implicit val swellDecoder: Decoder[Swell] = deriveDecoder[Swell]

    implicit def swellEntityDecoder[F[_] : Sync]: EntityDecoder[F, Swell] =
      jsonOf

    implicit val swellEncoder: Encoder[Swell] = deriveEncoder[Swell]

    implicit def swellEntityEncoder[F[_] : Applicative]: EntityEncoder[F, Swell] =
      jsonEncoderOf
  }


}