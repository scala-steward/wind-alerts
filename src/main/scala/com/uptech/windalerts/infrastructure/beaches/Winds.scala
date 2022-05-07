package com.uptech.windalerts.infrastructure.beaches

import cats.data.EitherT
import cats.effect.{Async, ContextShift, Sync}
import com.softwaremill.sttp._
import com.uptech.windalerts.core.UnknownError
import com.uptech.windalerts.core.beaches.domain
import com.uptech.windalerts.core.beaches.domain.BeachId
import com.uptech.windalerts.infrastructure.resilience
import com.uptech.windalerts.logger
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, parser}
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf

import scala.concurrent.Future


case class Wind(
                 speed: Double,
                 gustSpeed: Double,
                 trend: Double,
                 direction: Double,
                 directionText: String
               )

object Wind {

  implicit val windDecoder: Decoder[Wind] = deriveDecoder[Wind]

  implicit def windEntityDecoder[F[_] : Sync]: EntityDecoder[F, Wind] =
    jsonOf
}

object WWBackedWindsService {

  def get[F[_]](apiKey: String)(implicit backend: SttpBackend[Id, Nothing], F: Async[F], C: ContextShift[F]) = (beachId: BeachId) =>  {
    val future: Future[Id[Response[String]]] =
      resilience.willyWeatherRequestsDecorator(() => {
        logger.info(s"Fetching wind status for $beachId")
        val response = sttp.get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?observational=true").send()
        logger.info(s"Response from WW $response")
        response
      })

    EitherT(F.map(Async.fromFuture(F.pure(future)))(parse(_)))
  }

  private def parse(response: Id[Response[String]]) = {
    val res = for {
      body <- response
        .body
        .left
        .map(left => {
          WillyWeatherHelper.extractError(left)
        })
      parsed <- parser.parse(body).left.map(f => UnknownError(f.message))
      wind <- parsed.hcursor.downField("observational").downField("observations").downField("wind").as[Wind].left.map(f => UnknownError(f.message))
    } yield domain.Wind(wind.direction, wind.speed, wind.directionText, if (wind.trend > 0) "Increasing" else "Decreasing")

    WillyWeatherHelper.leftOnBeachNotFoundError(res, domain.Wind(Double.NaN, Double.NaN, "NA", "NA"))
  }

}

