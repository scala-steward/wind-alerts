package com.uptech.windalerts


import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.Domain.{BeachId, Swell}
import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.semiauto._
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method._
import org.http4s.circe._
import java.util.{Calendar, Date, TimeZone}
import Domain._
trait Swells[F[_]] {
  def get(beachId: BeachId): F[Domain.Swell]
}

object Swells {
  def apply[F[_]](implicit ev: Swells[F]): Swells[F] = ev

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

  final case class SwellError(e: Throwable) extends RuntimeException

  def impl[F[_] : Sync](C: Client[F]): Swells[F] = new Swells[F] {
    val dsl = new Http4sClientDsl[F] {}

    import dsl._
    import java.text.SimpleDateFormat

    val sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private def swellUri(beachId: Int): Uri = {
      val baseUri = Uri.uri("https://api.willyweather.com.au/")
      val withPath = baseUri.withPath(s"/v2/ZjM0ZmY1Zjc5NDQ3N2IzNjE3MmRmYm/locations/$beachId/weather.json")
      val withQuery = withPath.withQueryParam("forecasts", "swell").withQueryParam("days", 1)
      withQuery
    }

    def get(beachId: BeachId): F[Domain.Swell] = C.expect[String](GET(swellUri(beachId.id)))
      .adaptError { case t => SwellError(t) }
      .map(s => {
        val timeZoneStr = parser.parse(s).getOrElse(Json.Null).hcursor.downField("location").downField("timeZone").as[String]
        val timeZone = TimeZone.getTimeZone(timeZoneStr.getOrElse("Australia/Sydney"))
        parser.parse(s).getOrElse(Json.Null).hcursor.downField("forecasts").downField("swell")
          .downField("days").focus
          .get
          .hcursor
          .downArray
          .downField("entries")
          .values
          .get.flatMap(j => j.as[Swell].toSeq).filter(s =>
          {
            val entry = LocalDateTime.parse(s.dateTime, sdf ).atZone( timeZone.toZoneId ).withZoneSameInstant(TimeZone.getDefault.toZoneId)

            val currentTime = LocalDateTime.now()

            entry.getHour == currentTime.getHour
          })
          .head
        }
      )
      .map(swell => Domain.Swell(swell.height, swell.direction))
  }
}