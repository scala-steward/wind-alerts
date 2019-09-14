package com.uptech.windalerts.status

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone

import cats.Applicative
import cats.effect.{IO, Sync}
import com.softwaremill.sttp._
import com.uptech.windalerts.domain.Domain
import com.uptech.windalerts.domain.Domain.{BeachId, TideHeight}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json, parser}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}


trait Tides extends Serializable {
  val alerts: Tides.Service
}

object Tides {

  trait Service {
    def get(beachId: BeachId): IO[Domain.TideHeight]
  }

  val impl: Service = (beachId: BeachId) => {

    val request = sttp.get(uri"https://api.willyweather.com.au/v2/ZjM0ZmY1Zjc5NDQ3N2IzNjE3MmRmYm/locations/${beachId.id}/weather.json?forecasts=tides&days=1")
    implicit val backend = HttpURLConnectionBackend()
    val response = request.send()

    val eitherResponse = response.body.map(s => {
      val timeZoneStr = parser.parse(s).getOrElse(Json.Null).hcursor.downField("location").downField("timeZone").as[String]
      val timeZone = TimeZone.getTimeZone(timeZoneStr.getOrElse("Australia/Sydney"))
      val sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

      parser.parse(s).getOrElse(Json.Null).hcursor
        .downField("forecasts")
        .downField("tides")
        .downField("days").focus
        .get
        .hcursor
        .downArray
        .downField("entries")
        .values
        .get.flatMap(j => j.as[Tide].toSeq).filter(s => {
        val entry = LocalDateTime.parse(s.dateTime, sdf).atZone(timeZone.toZoneId).withZoneSameInstant(TimeZone.getDefault.toZoneId)

        val currentTime = LocalDateTime.now()
        entry.toLocalDateTime.isBefore(currentTime)
      })
        .maxBy(tide => tide.dateTime)
    }).map(tide => TideHeight(tide.`type`))

    val throwableEither = eitherResponse match {
      case Left(s) => Left(new RuntimeException(s))
      case Right(s) => Right(s)
    }
    IO.fromEither(throwableEither)
  }

  case class Tide(
                   dateTime: String,
                   height: Double,
                   `type`: String
                 )

  object Tide {
    implicit val tideDecoder: Decoder[Tide] = deriveDecoder[Tide]

    implicit def tideEntityDecoder[F[_] : Sync]: EntityDecoder[F, Tide] =
      jsonOf

    implicit val tideEncoder: Encoder[Tide] = deriveEncoder[Tide]

    implicit def tideEntityEncoder[F[_] : Applicative]: EntityEncoder[F, Tide] =
      jsonEncoderOf
  }

}