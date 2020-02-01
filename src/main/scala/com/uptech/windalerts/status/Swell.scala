package com.uptech.windalerts.status

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone

import cats.Applicative
import cats.effect.{IO, Sync}
import com.softwaremill.sttp._
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.BeachId
import com.uptech.windalerts.domain.swellAdjustments.{Adjustment, Adjustments}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.optics.JsonPath._
import io.circe.{Decoder, Encoder, Json, parser}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}
import org.log4s.getLogger

trait Swells extends Serializable {
  val alerts: Swells.Service
}

object Swells {
  private val logger = getLogger

  trait Service {
    def get(beachId: BeachId): IO[domain.Swell]
  }

  def impl(apiKey:String, adjustments: Adjustments): Service = (beachId: BeachId) => {

    val sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    val request = sttp.get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?forecasts=swell&days=1")
    implicit val backend = HttpURLConnectionBackend()
    val response = request.send()

    val eitherResponse = response.body.map(s => {
      val timeZoneStr = parser.parse(s).getOrElse(Json.Null).hcursor.downField("location").downField("timeZone").as[String]
      val timeZone = TimeZone.getTimeZone(timeZoneStr.getOrElse("Australia/Sydney"))
      val entries = root.forecasts.swell.days.each.entries.each.json.getAll(parser.parse(s).toOption.get)

      entries.flatMap(j => j.as[Swell].toSeq).filter(s => {
        val entry = LocalDateTime.parse(s.dateTime, sdf).atZone(timeZone.toZoneId).withZoneSameInstant(TimeZone.getDefault.toZoneId)

        entry.getHour == LocalDateTime.now().getHour
      }).map(swell=>swell.copy(height = adjustments.adjust(swell.height)))
        .head
    }
    ).map(swell => domain.Swell(swell.height, swell.direction, swell.directionText))

    val throwableEither = eitherResponse match {
      case Left(s) => {
        logger.error(s)
        Left(new RuntimeException(s))
      }
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