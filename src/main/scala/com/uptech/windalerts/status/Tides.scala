package com.uptech.windalerts.status

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZonedDateTime}

import cats.data.EitherT
import cats.effect.Sync
import cats.{Applicative, Functor}
import com.softwaremill.sttp._
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.{BeachId, TideHeight}
import com.uptech.windalerts.status.Tides.{Datum, TideDecoders}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.optics.JsonPath._
import io.circe.{Decoder, Encoder, parser}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}
import org.log4s.getLogger

class TidesService[F[_] : Sync](apiKey: String)(implicit backend: SttpBackend[Id, Nothing]) {
  private val logger = getLogger

  def get(beachId: BeachId)(implicit F: Functor[F]): EitherT[F, Exception, domain.TideHeight] =
    EitherT.fromEither(getFromWillyWeatther(apiKey, beachId))

  def getFromWillyWeatther(apiKey: String, beachId: BeachId): Either[Exception, domain.TideHeight] = {
    val startDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val startDateFormatted = startDateFormat.format(LocalDateTime.now().minusDays(1))
    logger.error(s"Duser.timezeone is ${System.getProperty("user.timezone")}")

    logger.error(s"LocalDateTime.now() is ${LocalDateTime.now()}")
    val currentTimeGmt = (System.currentTimeMillis() / 1000) + ZonedDateTime.now.getOffset.getTotalSeconds
    logger.error(s"currentTimeGmt is $currentTimeGmt")

    import TideDecoders._

    sttp.get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?forecastGraphs=tides&days=3&startDate=$startDateFormatted").send().body
      .left.map(new RuntimeException(_))
      .flatMap(parser.parse(_))
      .map(root.forecastGraphs.tides.dataConfig.series.groups.each.points.each.json.getAll(_))
      .map(_.flatMap(j => j.as[Datum].toSeq.sortBy(_.x)))
      .map(interpolate(currentTimeGmt, _))
  }

  private def interpolate(currentTimeGmt: Long, sorted: List[Datum]) = {
    val before = sorted.filterNot(s => s.x > currentTimeGmt)
    val after = sorted.filter(s => s.x > currentTimeGmt)

    val interpolated = before.last.interpolateWith(currentTimeGmt, after.head)
    val nextHigh_ = after.filter(_.description == "high").head
    val nextHigh = nextHigh_.copy(x = nextHigh_.x)
    val nextLow_ = after.filter(_.description == "low").head
    val nextLow = nextLow_.copy(x = nextLow_.x)

    val status = if (nextLow.x < nextHigh.x) "Falling" else "Rising"

    TideHeight(interpolated.y, status, nextLow.x, nextHigh.x)
  }
}


object Tides {


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