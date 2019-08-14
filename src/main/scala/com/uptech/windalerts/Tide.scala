package com.uptech.windalerts

import java.time.{LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter

import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.Domain.{BeachId, TideHeightStatus, TideStatus}
import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.semiauto._
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method._
import org.http4s.circe._
import java.util.{Calendar, Date, Locale, TimeZone}

trait Tides[F[_]] {
  def get(beachId: BeachId): F[TideHeightStatus]
}

object Tides {
  def apply[F[_]](implicit ev: Tides[F]): Tides[F] = ev

  case class Tide(
                    dateTime: String,
                    height: Double,
                    `type` : String
                  )

  object Tide {
    implicit val tideDecoder: Decoder[Tide] = deriveDecoder[Tide]

    implicit def tideEntityDecoder[F[_] : Sync]: EntityDecoder[F, Tide] =
      jsonOf

    implicit val tideEncoder: Encoder[Tide] = deriveEncoder[Tide]

    implicit def tideEntityEncoder[F[_] : Applicative]: EntityEncoder[F, Tide] =
      jsonEncoderOf
  }

  final case class TideError(e: Throwable) extends RuntimeException

  def impl[F[_] : Sync](C: Client[F]): Tides[F] = new Tides[F] {
    val dsl = new Http4sClientDsl[F] {}

    import dsl._
    import java.text.SimpleDateFormat

    private def tideUri(beachId: Int): Uri = {
      val baseUri = Uri.uri("https://api.willyweather.com.au/")
      val withPath = baseUri.withPath(s"/v2/ZjM0ZmY1Zjc5NDQ3N2IzNjE3MmRmYm/locations/$beachId/weather.json")
      val withQuery = withPath.withQueryParam("forecasts", "tides").withQueryParam("days", 1)
      withQuery
    }

    def get(beachId: BeachId): F[TideHeightStatus] = C.expect[String](GET(tideUri(beachId.id)))
      .adaptError { case t => TideError(t) }
      .map(s => {
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
          .get.flatMap(j => j.as[Tide].toSeq).filter(s =>
          {
            val entry = LocalDateTime.parse(s.dateTime, sdf ).atZone( timeZone.toZoneId )

            val currentTime = LocalDateTime.now()
            entry.toLocalDateTime.isBefore(currentTime)
          })
          .maxBy(tide => tide.dateTime)
        }).map(tide => TideHeightStatus(tide.`type`))

  }
}