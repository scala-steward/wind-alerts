package com.uptech.windalerts.infrastructure.beaches

import cats.Applicative
import cats.data.EitherT
import cats.effect.{Async, ContextShift, Sync}
import com.softwaremill.sttp._
import com.uptech.windalerts.core.beaches.SwellsService
import com.uptech.windalerts.core.{SurfsUpError, UnknownError}
import com.uptech.windalerts.core.beaches.domain
import com.uptech.windalerts.core.beaches.domain.BeachId
import com.uptech.windalerts.core.beaches.domain
import com.uptech.windalerts.config.swellAdjustments.Adjustments
import com.uptech.windalerts.infrastructure.beaches.Swells.Swell
import com.uptech.windalerts.infrastructure.resilience
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.optics.JsonPath._
import io.circe.{Decoder, Encoder, parser}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}
import org.log4s.getLogger

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import scala.concurrent.Future

class WWBackedSwellsService[F[_] : Sync](apiKey: String, adjustments: Adjustments)(implicit backend: SttpBackend[Id, Nothing], F: Async[F], C: ContextShift[F]) extends SwellsService[F] {
  private val logger = getLogger

  override def get(beachId: BeachId): cats.data.EitherT[F, SurfsUpError, domain.Swell] =
    getFromWillyWeatther_(apiKey, beachId)

  def getFromWillyWeatther_(apiKey: String, beachId: BeachId) = {
    val future: Future[Id[Response[String]]] =
      resilience.willyWeatherRequestsDecorator(() => {
        logger.error(s"Fetching swell status for $beachId")
        sttp.get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?forecasts=swell&days=1")
          .send()
      })

    EitherT(F.map(Async.fromFuture(F.pure(future)))(parse(_)))
  }

  def parse(response: Id[Response[String]])= {
    val res = for {
      body <- response
        .body
        .left
        .map(left => {
          WillyWeatherHelper.extractError(left)
        })
      parsed <- parser.parse(body).left.map(f => UnknownError(f.message))
      tz <- parsed.hcursor.downField("location").downField("timeZone").as[String].left.map(f => UnknownError(f.message))
      forecasts = root.forecasts.swell.days.each.entries.each.json.getAll(parsed)
      swells = forecasts.flatMap(_.as[Swell].toSeq.filter(isCurrentHour(tz, _)))
      adjusted <- swells.map(swell => swell.copy(height = adjustments.adjust(swell.height))).headOption.toRight(UnknownError("Empty response from WW"))
      domainSwell = domain.Swell(adjusted.height, adjusted.direction, adjusted.directionText)
    } yield domainSwell

    WillyWeatherHelper.leftOnBeachNotFoundError(res, domain.Swell(Double.NaN, Double.NaN, "NA"))

  }

  private def isCurrentHour(tz: String, s: Swell) = {
    val sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val timeZone = TimeZone.getTimeZone(tz)
    val entry = LocalDateTime.parse(s.dateTime, sdf).atZone(timeZone.toZoneId).withZoneSameInstant(TimeZone.getDefault.toZoneId)
    entry.getHour == LocalDateTime.now().getHour
  }
}

object Swells {


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