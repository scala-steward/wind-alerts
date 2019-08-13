package com.uptech.windalerts

import cats.Applicative
import cats.effect.Sync
import com.uptech.windalerts.Domain.{BeachStatus, SwellStatus, TideHeightStatus, TideStatus, WindStatus}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.http4s.{EntityDecoder, EntityEncoder}
import org.http4s.circe.{jsonEncoderOf, jsonOf}

object DomainCodec {

  lazy implicit val beachDecoder: Decoder[BeachStatus] = deriveDecoder[BeachStatus]

  implicit def beachEntityDecoder[F[_] : Sync]: EntityDecoder[F, BeachStatus] =
    jsonOf

  lazy implicit val beachEncoder: Encoder[BeachStatus] = deriveEncoder[BeachStatus]

  implicit def beachEntityEncoder[F[_] : Applicative]: EntityEncoder[F, BeachStatus] =
    jsonEncoderOf

  lazy implicit val swellDecoder: Decoder[SwellStatus] = deriveDecoder[SwellStatus]

  implicit def swellEntityDecoder[F[_] : Sync]: EntityDecoder[F, SwellStatus] =
    jsonOf

  lazy implicit val swellEncoder: Encoder[SwellStatus] = deriveEncoder[SwellStatus]

  implicit def swellEntityEncoder[F[_] : Applicative]: EntityEncoder[F, SwellStatus] =
    jsonEncoderOf

  lazy implicit val windDecoder: Decoder[WindStatus] = deriveDecoder[WindStatus]

  implicit def windEntityDecoder[F[_] : Sync]: EntityDecoder[F, WindStatus] =
    jsonOf

  lazy implicit val windEncoder: Encoder[WindStatus] = deriveEncoder[WindStatus]

  implicit def windEntityEncoder[F[_] : Applicative]: EntityEncoder[F, WindStatus] =
    jsonEncoderOf

  lazy implicit val tideDecoder: Decoder[TideStatus] = deriveDecoder[TideStatus]

  implicit def tideEntityDecoder[F[_] : Sync]: EntityDecoder[F, TideStatus] =
    jsonOf

  lazy implicit val tideEncoder: Encoder[TideStatus] = deriveEncoder[TideStatus]

  implicit def tideEntityEncoder[F[_] : Applicative]: EntityEncoder[F, TideStatus] =
    jsonEncoderOf

  lazy implicit val tideHeightDecoder: Decoder[TideHeightStatus] = deriveDecoder[TideHeightStatus]

  implicit def tideHeightEntityDecoder[F[_] : Sync]: EntityDecoder[F, TideHeightStatus] =
    jsonOf

  lazy implicit val tideHeightEncoder: Encoder[TideHeightStatus] = deriveEncoder[TideHeightStatus]

  implicit def tideHeightEntityEncoder[F[_] : Applicative]: EntityEncoder[F, TideHeightStatus] =
    jsonEncoderOf
}
