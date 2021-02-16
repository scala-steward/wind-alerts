package com.uptech.windalerts.status
import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import cats.data.EitherT
import cats.effect.Sync
import cats.{Applicative, Functor}
import com.softwaremill.sttp._
import com.uptech.windalerts.Repos
import com.uptech.windalerts.domain.domain.{BeachId, SurfsUpEitherT, TideHeight}
import com.uptech.windalerts.domain.{UnknownError, beaches, domain}
import com.uptech.windalerts.status.Tides.{Datum, TideDecoders}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.optics.JsonPath._
import io.circe.{Decoder, Encoder, parser}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}
import org.log4s.getLogger

class TidesService[F[_] : Sync](apiKey: String, repos:Repos[F])(implicit backend: SttpBackend[Id, Nothing]) {
  private val logger = getLogger
  val startDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  val timeZoneForRegion = Map("TAS" ->"Australia/Tasmania",
                               "WA" -> "Australia/Perth",
                              "VIC" -> "Australia/Victoria",
                              "QLD" -> "Australia/Queensland",
                               "SA" -> "Australia/Adelaide",
                              "ACT" -> "Australia/ACT",
                             "NSW"  -> "Australia/NSW",
                              "NT"  -> "Australia/Darwin")
  def get(beachId: BeachId)(implicit F: Functor[F]): SurfsUpEitherT[F, domain.TideHeight] =
    EitherT.fromEither(getFromWillyWeatther(apiKey, beachId))

  def getFromWillyWeatther(apiKey: String, beachId: BeachId) = {
    logger.error(s"Fetching tides status for $beachId")

    val tz = timeZoneForRegion.getOrElse(repos.beaches()(beachId.id).region, "Australia/NSW")
    val tzId = ZoneId.of(tz)
    val startDateFormatted = startDateFormat.format(ZonedDateTime.now(tzId).minusDays(1))

    val currentTimeGmt = (System.currentTimeMillis() / 1000) + ZonedDateTime.now(tzId).getOffset.getTotalSeconds

    import TideDecoders._

    sttp.get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?forecastGraphs=tides&days=3&startDate=$startDateFormatted").send().body
      .left.map(UnknownError(_))
      .flatMap(parser.parse(_))
      .left.map(e=>UnknownError(e.getMessage))
      .map(root.forecastGraphs.tides.dataConfig.series.groups.each.points.each.json.getAll(_))
      .map(_.flatMap(j => j.as[Datum].toSeq.sortBy(_.x)))
      .map(interpolate(tzId, currentTimeGmt, _))
      .orElse(Right(domain.TideHeight(Double.NaN, "NA", Long.MinValue, Long.MinValue)))
  }

  private def interpolate(zoneId:ZoneId, currentTimeGmt: Long, sorted: List[Datum]) = {
    try {
      val before = sorted.filterNot(s => s.x > currentTimeGmt)
      val after = sorted.filter(s => s.x > currentTimeGmt)

      val interpolated = before.last.interpolateWith(currentTimeGmt, after.head)
      val nextHigh_ = after.filter(_.description == "high").head
      val nextHigh = nextHigh_.copy(x = nextHigh_.x - ZonedDateTime.now(zoneId).getOffset.getTotalSeconds)
      val nextLow_ = after.filter(_.description == "low").head
      val nextLow = nextLow_.copy(x = nextLow_.x - ZonedDateTime.now(zoneId).getOffset.getTotalSeconds)

      val status = if (nextLow.x < nextHigh.x) "Decreasing" else "Increasing"

      TideHeight(interpolated.y, status, nextLow.x, nextHigh.x)
    }catch {
      case e: Exception => {
        logger.error(e)(s"error while interpolating tide")
        TideHeight(Double.NaN, "NA", Long.MinValue, Long.MinValue)
      }
    }
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