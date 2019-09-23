package com.uptech.windalerts.domain

import cats.Applicative
import cats.effect.Sync
import com.uptech.windalerts.domain.Domain._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

object DomainCodec {

  lazy implicit val beachDecoder: Decoder[Beach] = deriveDecoder[Beach]

  implicit def beachEntityDecoder[F[_] : Sync]: EntityDecoder[F, Beach] = jsonOf

  lazy implicit val beachEncoder: Encoder[Beach] = deriveEncoder[Beach]

  implicit def beachEntityEncoder[F[_] : Applicative]: EntityEncoder[F, Beach] = jsonEncoderOf

  lazy implicit val swellDecoder: Decoder[Swell] = deriveDecoder[Swell]

  implicit def swellEntityDecoder[F[_] : Sync]: EntityDecoder[F, Swell] = jsonOf

  lazy implicit val swellEncoder: Encoder[Swell] = deriveEncoder[Swell]

  implicit def swellEntityEncoder[F[_] : Applicative]: EntityEncoder[F, Swell] = jsonEncoderOf

  lazy implicit val windDecoder: Decoder[Wind] = deriveDecoder[Wind]

  implicit def windEntityDecoder[F[_] : Sync]: EntityDecoder[F, Wind] = jsonOf

  lazy implicit val windEncoder: Encoder[Wind] = deriveEncoder[Wind]

  implicit def windEntityEncoder[F[_] : Applicative]: EntityEncoder[F, Wind] = jsonEncoderOf

  lazy implicit val tideDecoder: Decoder[Tide] = deriveDecoder[Tide]

  implicit def tideEntityDecoder[F[_] : Sync]: EntityDecoder[F, Tide] = jsonOf

  lazy implicit val tideEncoder: Encoder[Tide] = deriveEncoder[Tide]

  implicit def tideEntityEncoder[F[_] : Applicative]: EntityEncoder[F, Tide] = jsonEncoderOf

  lazy implicit val tideHeightDecoder: Decoder[TideHeight] = deriveDecoder[TideHeight]

  implicit def tideHeightEntityDecoder[F[_] : Sync]: EntityDecoder[F, TideHeight] = jsonOf

  lazy implicit val tideHeightEncoder: Encoder[TideHeight] = deriveEncoder[TideHeight]

  implicit def tideHeightEntityEncoder[F[_] : Applicative]: EntityEncoder[F, TideHeight] = jsonEncoderOf

  lazy implicit val alertDecoder: Decoder[Alert] = deriveDecoder[Alert]

  implicit def alertEntityDecoder[F[_] : Sync]: EntityDecoder[F, Alert] = jsonOf

  lazy implicit val alertEncoder: Encoder[Alert] = deriveEncoder[Alert]

  implicit def alertEntityEncoder[F[_] : Applicative]: EntityEncoder[F, Alert] = jsonEncoderOf

  lazy implicit val timeRangeDecoder: Decoder[TimeRange] = deriveDecoder[TimeRange]

  implicit def timeRangeEntityDecoder[F[_] : Sync]: EntityDecoder[F, TimeRange] = jsonOf

  lazy implicit val timeRangeEncoder: Encoder[TimeRange] = deriveEncoder[TimeRange]

  implicit def timeRangeEntityEncoder[F[_] : Applicative]: EntityEncoder[F, TimeRange] = jsonEncoderOf

  lazy implicit val userDecoder: Decoder[User] = deriveDecoder[User]

  implicit def userEntityDecoder[F[_] : Sync]: EntityDecoder[F, User] = jsonOf

  lazy implicit val userEncoder: Encoder[User] = deriveEncoder[User]

  implicit def userEntityEncoder[F[_] : Applicative]: EntityEncoder[F, User] = jsonEncoderOf

  lazy implicit val userDeviceDecoder: Decoder[UserDevice] = deriveDecoder[UserDevice]

  implicit def userDeviceEntityDecoder[F[_] : Sync]: EntityDecoder[F, UserDevice] = jsonOf

  lazy implicit val userDeviceEncoder: Encoder[UserDevice] = deriveEncoder[UserDevice]

  implicit def userDeviceEntityEncoder[F[_] : Applicative]: EntityEncoder[F, UserDevice] = jsonEncoderOf

  lazy implicit val userDeviceRDecoder: Decoder[DeviceRequest] = deriveDecoder[DeviceRequest]

  implicit def userDeviceEntityRDecoder[F[_] : Sync]: EntityDecoder[F, DeviceRequest] = jsonOf

  lazy implicit val userDeviceREncoder: Encoder[DeviceRequest] = deriveEncoder[DeviceRequest]

  implicit def userDeviceEntityREncoder[F[_] : Applicative]: EntityEncoder[F, DeviceRequest] = jsonEncoderOf

  lazy implicit val userDevicesDecoder: Decoder[UserDevices] = deriveDecoder[UserDevices]

  implicit def userDevicesEntityLDecoder[F[_] : Sync]: EntityDecoder[F, UserDevices] = jsonOf

  lazy implicit val userDevicesEncoder: Encoder[UserDevices] = deriveEncoder[UserDevices]

  implicit def userDevicesEntityLEncoder[F[_] : Applicative]: EntityEncoder[F, UserDevices] = jsonEncoderOf

  lazy implicit val alertRDecoder: Decoder[AlertRequest] = deriveDecoder[AlertRequest]

  implicit def alertRntityDecoder[F[_] : Sync]: EntityDecoder[F, AlertRequest] = jsonOf

  lazy implicit val alertREncoder: Encoder[AlertRequest] = deriveEncoder[AlertRequest]

  implicit def alertREntityEncoder[F[_] : Applicative]: EntityEncoder[F, AlertRequest] = jsonEncoderOf

  lazy implicit val salertDecoder: Decoder[Alerts] = deriveDecoder[Alerts]

  implicit def salertEntityDecoder[F[_] : Sync]: EntityDecoder[F, Alerts] = jsonOf

  lazy implicit val salertEncoder: Encoder[Alerts] = deriveEncoder[Alerts]

  implicit def salertEntityEncoder[F[_] : Applicative]: EntityEncoder[F, Alerts] = jsonEncoderOf
}
