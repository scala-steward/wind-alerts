package com.uptech.windalerts.status

import java.time.{LocalDateTime, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.TimeZone

import cats.{Applicative, Functor}
import cats.data.EitherT
import cats.effect.{IO, Sync}
import com.softwaremill.sttp._
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.{BeachId, TideHeight}
import com.uptech.windalerts.status.Tides.{Datum, TideDecoders}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json, parser}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}
import io.circe.optics.JsonPath._


class TidesService[F[_] : Sync](apiKey: String)(implicit backend: HttpURLConnectionBackend) {
  def get(beachId: BeachId)(implicit F: Functor[F]): EitherT[F, Exception, domain.TideHeight] =
    EitherT.fromEither(getFromWillyWeatther(apiKey, beachId))

  def getFromWillyWeatther(apiKey: String, beachId: BeachId): Either[Exception, domain.Wind] = {
    val startDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val startDateFormatted = startDateFormat.format(LocalDateTime.now().minusDays(1))
    val currentTimeGmt = (System.currentTimeMillis() / 1000) + ZonedDateTime.now.getOffset.getTotalSeconds
    import TideDecoders._

    sttp.get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?forecastGraphs=tides&days=3&startDate=$startDateFormatted").send().body
      .left.map(new RuntimeException(_))
      .flatMap(s => {
        val x = root.forecastGraphs.tides.dataConfig.series.groups.each.points.each.json.getAll(parser.parse(s).toOption.get)
        null
      })
    for {
      a1 <- sttp.get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?forecastGraphs=tides&days=3&startDate=$startDateFormatted").send().body
      a2 <- root.forecastGraphs.tides.dataConfig.series.groups.each.points.each.json.getAll(parser.parse(a1).toOption.get)
    } yield ()
    null
  }

}


trait Tides extends Serializable {
  val alerts: Tides.Service
}

object Tides {

  trait Service {
    def get(beachId: BeachId): IO[domain.TideHeight]
  }

  def impl(apiKey: String): Service = (beachId: BeachId) => {
    val startDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val startDateFormatted = startDateFormat.format(LocalDateTime.now().minusDays(1))
    val request = sttp.get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?forecastGraphs=tides&days=3&startDate=$startDateFormatted")
    implicit val backend = HttpURLConnectionBackend()
    val response = request.send()
    val currentTimeGmt = (System.currentTimeMillis() / 1000) + ZonedDateTime.now.getOffset.getTotalSeconds
    val eitherResponse = response.body.map(s => {
      import TideDecoders._

      val _entries = root.forecastGraphs.tides.dataConfig.series.groups.each.points.each.json.getAll(parser.parse(s).toOption.get)
      val sorted:List[Datum] = _entries.flatMap(j => j.as[Datum].toSeq.sortBy(s => {
        s.x
      }))
      val before = sorted.filterNot(s => s.x > currentTimeGmt)
      val after = sorted.filter(s => s.x  > currentTimeGmt)
      val interpolated = before.last.interpolateWith(currentTimeGmt, after.head)


      val nextHigh_ = after.filter(_.description == "high").head
      val nextHigh = nextHigh_.copy(x = nextHigh_.x - ZonedDateTime.now.getOffset.getTotalSeconds)
      val nextLow_ = after.filter(_.description == "low").head
      val nextLow = nextLow_.copy(x = nextLow_.x - ZonedDateTime.now.getOffset.getTotalSeconds)

      val status = if (nextLow.x < nextHigh.x) "Falling" else "Rising"

      TideHeight(interpolated.y, status, nextLow.x, nextHigh.x)
    })

    val throwableEither = eitherResponse match {
      case Left(s) => Left(new RuntimeException(s))
      case Right(s) => Right(s)
    }
    IO.fromEither(throwableEither)
  }

  case class Datum(
                    x: Long,
                    y: Double,
                    description: String,
                    interpolated: Boolean
                  ) {
    def interpolateWith(newX: Long, other: Datum) =
      Datum(newX, BigDecimal((other.y - y) / (other.x - x) * (newX - x) + y).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble, "", true)
  }

  case class Tide(
                   dateTime: String,
                   height: Double,
                   `type`: String
                 )

  object TideDecoders {
    implicit val tideDecoder: Decoder[Datum] = deriveDecoder[Datum]

    implicit def tideEntityDecoder[F[_] : Sync]: EntityDecoder[F, Datum] =
      jsonOf

    implicit val tideEncoder: Encoder[Datum] = deriveEncoder[Datum]

    implicit def tideEntityEncoder[F[_] : Applicative]: EntityEncoder[F, Datum] =
      jsonEncoderOf
  }

}