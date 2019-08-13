package com.uptech.windalerts


import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.Domain.{BeachId, SwellStatus}
import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.semiauto._
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method._
import org.http4s.circe._
import java.util.{Calendar, Date, TimeZone}

trait Swells[F[_]] {
  def get(beachId: BeachId): F[Option[SwellStatus]]
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

    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private def swellUri(beachId: Int): Uri = {
      val baseUri = Uri.uri("https://api.willyweather.com.au/")
      val withPath = baseUri.withPath(s"/v2/ZjM0ZmY1Zjc5NDQ3N2IzNjE3MmRmYm/locations/$beachId/weather.json")
      val withQuery = withPath.withQueryParam("forecasts", "swell").withQueryParam("days", 1)
      withQuery
    }

    def get(beachId: BeachId): F[Option[SwellStatus]] = C.expect[String](GET(swellUri(beachId.id)))
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
            val cal = Calendar.getInstance()
            cal.setTimeZone(timeZone)
            val cal2 = Calendar.getInstance()
            cal2.setTime(sdf.parse(s.dateTime))
            cal.get(Calendar.HOUR_OF_DAY) == cal2.get(Calendar.HOUR_OF_DAY)
          })
          .headOption
        }.map(swell => new SwellStatus(swell.height, swell.direction))
      )
  }
}