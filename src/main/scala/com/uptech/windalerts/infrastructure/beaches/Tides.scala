package com.uptech.windalerts.infrastructure.beaches

import cats.Applicative
import cats.data.EitherT
import cats.effect.{Async, ContextShift, Sync}
import com.softwaremill.sttp._
import com.uptech.windalerts.core.beaches.domain.{BeachId, TideHeight}
import com.uptech.windalerts.core.beaches.{TidesService, domain}
import com.uptech.windalerts.core.{BeachNotFoundError, SurfsUpError, UnknownError}
import com.uptech.windalerts.infrastructure.beaches.Tides.Datum
import com.uptech.windalerts.infrastructure.beaches.Tides.TideDecoders.tideDecoder
import com.uptech.windalerts.infrastructure.resilience
import com.uptech.windalerts.logger
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.optics.JsonPath._
import io.circe.{Decoder, Encoder, parser}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import scala.concurrent.Future


class WWBackedTidesService[F[_] : Sync](apiKey: String, beachesConfig: Map[Long, com.uptech.windalerts.config.beaches.Beach])(implicit backend: SttpBackend[Id, Nothing], F: Async[F], C: ContextShift[F])
extends TidesService[F] {
  val startDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  val timeZoneForRegion = Map("TAS" -> "Australia/Tasmania",
    "WA" -> "Australia/Perth",
    "VIC" -> "Australia/Victoria",
    "QLD" -> "Australia/Queensland",
    "SA" -> "Australia/Adelaide",
    "ACT" -> "Australia/ACT",
    "NSW" -> "Australia/NSW",
    "NT" -> "Australia/Darwin")

  override def get(beachId: BeachId): cats.data.EitherT[F, SurfsUpError, domain.TideHeight] =
    getFromWillyWeatther_(beachId)


  def getFromWillyWeatther_(beachId: BeachId): cats.data.EitherT[F, SurfsUpError, domain.TideHeight] = {
    logger.info(s"Fetching tides status for $beachId")

    if (!beachesConfig.contains(beachId.id)) {
      EitherT.left[domain.TideHeight](F.pure(BeachNotFoundError("Beach not found")))
    } else {
      val tz = timeZoneForRegion.getOrElse(beachesConfig(beachId.id).region, "Australia/NSW")
      val tzId = ZoneId.of(tz)
      val startDateFormatted = startDateFormat.format(ZonedDateTime.now(tzId).minusDays(1))

      val currentTimeGmt = (System.currentTimeMillis() / 1000) + ZonedDateTime.now(tzId).getOffset.getTotalSeconds
      sendRequestAndParseResponse(beachId, startDateFormatted, tzId, currentTimeGmt)
    }
  }

  def sendRequestAndParseResponse(beachId: BeachId, startDateFormatted: String, tzId: ZoneId, currentTimeGmt:Long) = {
    val future: Future[Id[Response[String]]] =
      resilience.willyWeatherRequestsDecorator(() => {
        sttp.get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?forecastGraphs=tides&days=3&startDate=$startDateFormatted").send()
      })

    EitherT(F.map(Async.fromFuture(F.pure(future)))(response=>parse(response, tzId, currentTimeGmt)))
  }


  def parse(response: Id[Response[String]], tzId: ZoneId, currentTimeGmt:Long) = {
    val res = for {
      body <- response
        .body
        .left
        .map(left => {
          WillyWeatherHelper.extractError(left)
        })
      parsed <- parser.parse(body).left.map(f => UnknownError(f.message))
      forecasts = root.forecastGraphs.tides.dataConfig.series.groups.each.points.each.json.getAll(parsed)
      datums = forecasts.flatMap(_.as[Datum].toSeq.sortBy(_.x))
      interpolated = interpolate(tzId, currentTimeGmt, datums)
    } yield interpolated

    WillyWeatherHelper.leftOnBeachNotFoundError(res, domain.TideHeight(Double.NaN, "NA", Long.MinValue, Long.MinValue))
  }



  private def interpolate(zoneId: ZoneId, currentTimeGmt: Long, sorted: List[Datum]) = {
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
    } catch {
      case e: Exception => {
        logger.info(e)(s"error while interpolating tide")
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