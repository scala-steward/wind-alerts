package com.uptech.windalerts


import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.Domain.{BeachId, Wind}
import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.semiauto._
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method._
import org.http4s.circe._
import java.util.{Calendar, Date, TimeZone}

trait Winds[F[_]] {
  def get(beachId: BeachId): F[Domain.Wind]
}

object Winds {
  def apply[F[_]](implicit ev: Winds[F]): Winds[F] = ev

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

    implicit val windEncoder: Encoder[Wind] = deriveEncoder[Wind]

    implicit def windEntityEncoder[F[_] : Applicative]: EntityEncoder[F, Wind] =
      jsonEncoderOf
  }

  final case class WindError(e: Throwable) extends RuntimeException

  def impl[F[_] : Sync](C: Client[F]): Winds[F] = new Winds[F] {
    val dsl = new Http4sClientDsl[F] {}

    import dsl._

    private def windUri(beachId: Int): Uri =
      Uri.uri("https://api.willyweather.com.au/")
        .withPath(s"/v2/ZjM0ZmY1Zjc5NDQ3N2IzNjE3MmRmYm/locations/$beachId/weather.json")
        .withQueryParam("observational", "true")

    def get(beachId: BeachId): F[Domain.Wind] = C.expect[String](GET(windUri(beachId.id)))
      .adaptError { case t => WindError(t) }
      .map(s => {
        parser.parse(s).getOrElse(Json.Null).hcursor.downField("observational").downField("observations")
          .downField("wind").as[Wind].map(wind => Domain.Wind(wind.direction, wind.speed))
          .getOrElse(Domain.Wind(0.0, 0.0))
      })
  }
}