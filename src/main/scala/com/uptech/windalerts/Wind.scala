package com.uptech.windalerts
import io.circe.parser
import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import io.circe.{ACursor, Decoder, Encoder, HCursor, Json, JsonObject}
import org.http4s.Method.GET
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{EntityDecoder, EntityEncoder, _}
import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.Winds.Points
import io.circe.generic.semiauto._
import org.http4s._
import org.http4s.implicits._
import org.http4s.{EntityDecoder, EntityEncoder, Method, Request, Uri}
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method._
import org.http4s.circe._

trait Winds[F[_]] {
  def get: F[Points]
}

object Winds {

  def apply[F[_]](implicit ev: Winds[F]): Winds[F] = ev

//  object RI {

    implicit val rootDecoder: Decoder[RootInterface] = deriveDecoder[RootInterface]
    implicit val windDecoder: Decoder[Wind] = deriveDecoder[Wind]
    implicit val units1Decoder: Decoder[Units1] = deriveDecoder[Units1]
    implicit val unitsDecoder: Decoder[Units] = deriveDecoder[Units]
    implicit val seriesDecoder: Decoder[Series] = deriveDecoder[Series]
    implicit val providerDecoder: Decoder[Provider] = deriveDecoder[Provider]
    implicit val pointsDecoder: Decoder[Points] = deriveDecoder[Points]
    implicit val pointsStyleDecoder: Decoder[PointStyle] = deriveDecoder[PointStyle]
    implicit val observationalGraphsDecoder: Decoder[ObservationalGraphs] = deriveDecoder[ObservationalGraphs]
    implicit val locationDecoder: Decoder[Location] = deriveDecoder[Location]
    implicit val groupsDecoder: Decoder[Groups] = deriveDecoder[Groups]
    implicit val dataConfigDecoder: Decoder[DataConfig] = deriveDecoder[DataConfig]
    implicit val controlPointsDecoder: Decoder[ControlPoints] = deriveDecoder[ControlPoints]
    implicit val configPointsDecoder: Decoder[Config] = deriveDecoder[Config]
    implicit val carouselPointsDecoder: Decoder[Carousel] = deriveDecoder[Carousel]

    implicit val rootEncoder: Encoder[RootInterface] = deriveEncoder[RootInterface]
    implicit val windEncoder: Encoder[Wind] = deriveEncoder[Wind]
    implicit val units1Encoder: Encoder[Units1] = deriveEncoder[Units1]
    implicit val unitsEncoder: Encoder[Units] = deriveEncoder[Units]
    implicit val seriesEncoder: Encoder[Series] = deriveEncoder[Series]
    implicit val providerEncoder: Encoder[Provider] = deriveEncoder[Provider]
    implicit val pointsEncoder: Encoder[Points] = deriveEncoder[Points]
    implicit val pointsStyleEncoder: Encoder[PointStyle] = deriveEncoder[PointStyle]
    implicit val observationalGraphsEncoder: Encoder[ObservationalGraphs] = deriveEncoder[ObservationalGraphs]
    implicit val locationEncoder: Encoder[Location] = deriveEncoder[Location]
    implicit val groupsEncoder: Encoder[Groups] = deriveEncoder[Groups]
    implicit val dataConfigEncoder: Encoder[DataConfig] = deriveEncoder[DataConfig]
    implicit val controlPointsEncoder: Encoder[ControlPoints] = deriveEncoder[ControlPoints]
    implicit val configPointsEncoder: Encoder[Config] = deriveEncoder[Config]
    implicit val carouselPointsEncoder: Encoder[Carousel] = deriveEncoder[Carousel]



    implicit def rootEntityDecoder[F[_]: Sync]: EntityDecoder[F, RootInterface] = jsonOf
    implicit def windEntityDecoder[F[_]: Sync]: EntityDecoder[F, Wind] = jsonOf
    implicit def units1EntityDecoder[F[_]: Sync]: EntityDecoder[F, Units1] = jsonOf
    implicit def unitsEntityDecoder[F[_]: Sync]: EntityDecoder[F, Units] = jsonOf
    implicit def seriesEntityDecoder[F[_]: Sync]: EntityDecoder[F, Series] = jsonOf
    implicit def providerEntityDecoder[F[_]: Sync]: EntityDecoder[F, Provider] = jsonOf
    implicit def pointsEntityDecoder[F[_]: Sync]: EntityDecoder[F, Points] = jsonOf
    implicit def pointStyleEntityDecoder[F[_]: Sync]: EntityDecoder[F, PointStyle] = jsonOf
    implicit def observationalGraphsEntityDecoder[F[_]: Sync]: EntityDecoder[F, ObservationalGraphs] = jsonOf
    implicit def locationEntityDecoder[F[_]: Sync]: EntityDecoder[F, Location] = jsonOf
    implicit def groupsEntityDecoder[F[_]: Sync]: EntityDecoder[F, Groups] = jsonOf
    implicit def dataConfigEntityDecoder[F[_]: Sync]: EntityDecoder[F, DataConfig] = jsonOf
    implicit def controlPointsEntityDecoder[F[_]: Sync]: EntityDecoder[F, ControlPoints] = jsonOf
    implicit def configEntityDecoder[F[_]: Sync]: EntityDecoder[F, Config] = jsonOf

    implicit def windEntityEncoder[F[_]: Applicative]: EntityEncoder[F, Wind] =
      jsonEncoderOf

    implicit def rootEntityEncoder[F[_]: Applicative]: EntityEncoder[F, RootInterface] =
      jsonEncoderOf

  implicit def pointsEntityEncoder[F[_]: Applicative]: EntityEncoder[F, Points] =
    jsonEncoderOf
//  }


  case class Carousel(
                       size: Int,
                       start: Int
                     )

  case class Config(
                     id: String,
                     color: String,
                     lineWidth: Int,
                     lineFill: Boolean,
                     lineRenderer: String,
                     showPoints: Boolean,
                     pointRenderer: String,
                     pointFormatter: String
                   )

  case class ControlPoints(
                            pre: Points,
                            post: String
                          )

  case class DataConfig(
                         series: Series,
                         xAxisMin: Int,
                         xAxisMax: Int
                       )

  case class Groups(
                     dateTime: Int,
                     points: Seq[Points]
                   )

  case class Location(
                       id: Int,
                       name: String,
                       region: String,
                       state: String,
                       postcode: String,
                       timeZone: String,
                       lat: Double,
                       lng: Double,
                       typeId: Int
                     )

  case class ObservationalGraphs(
                                  wind: Wind
                                )

  case class PointStyle(
                         fill: String,
                         stroke: String
                       )

  case class Points(
                     x: Int,
                     y: Double,
                     direction: Int,
                     directionText: String,
                     description: String,
                     pointStyle: PointStyle
                   )

  case class Provider(
                       id: Int,
                       name: String,
                       lat: Double,
                       lng: Double,
                       distance: Double,
                       units: Units1
                     )

  case class RootInterface(
                            location: Location,
                            observationalGraphs: ObservationalGraphs
                          )

  case class Series(
                     config: Config,
                     yAxisDataMin: Double,
                     yAxisDataMax: Double,
                     yAxisMin: Int,
                     yAxisMax: Int,
                     groups: Seq[Groups],
                     controlPoints: ControlPoints,
                     controlPoint: String
                   )

  case class Units(
                    speed: String
                  )

  case class Units1(
                     distance: String
                   )

  case class Wind(
                   dataConfig: DataConfig,
                   carousel: Carousel,
                   units: Units,
                   issueDateTime: String,
                   provider: Provider
                 )
  final case class WindError(e: Throwable) extends RuntimeException





  def impl[F[_] : Sync](C: Client[F]): Winds[F] = new Winds[F] {
    val dsl = new Http4sClientDsl[F] {}
    import dsl._
    case class Member(name: String)
    implicit val memberDecoder: Decoder[Member] =
      (hCursor: HCursor) => {
        for {
          name <- hCursor.get[String]("location")
        } yield Member(name)
      }

    def get: F[Points] = {
      C.expect[String](GET(uri"https://api.willyweather.com.au/v2/ZjM0ZmY1Zjc5NDQ3N2IzNjE3MmRmYm/locations/19167/weather.json?observationalGraphs=wind"))
        .adaptError { case t => {
          println(t)
          WindError(t)
        } }
        .map(a=> {

          val focus = parser.parse(a).getOrElse(Json.Null).hcursor
            .downField("observationalGraphs")
            .downField("wind")
            .downField("dataConfig")
            .downField("series")
            .downField("groups")
            .focus

          val values = focus.get.hcursor.downArray.downField("points").values
          values.get.map(j=>j.as[Points])
          val it = values.get.map(j => {
            val k = j.as[Points]
            k.getOrElse(new Points(0, 0, 0, "", "", new PointStyle("", "")))
          })

          it.toList.maxBy(a => a.x)
        })

    }
  }
}
