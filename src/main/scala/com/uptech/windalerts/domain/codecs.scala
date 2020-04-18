package com.uptech.windalerts.domain

import cats.Applicative
import cats.effect.Sync
import com.uptech.windalerts.domain.domain._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}

object codecs {
  Notification
  val codecRegistry = fromRegistries(
    fromProviders(classOf[Notification]),
    fromProviders(classOf[OTPWithExpiry]),
    fromProviders(classOf[AndroidToken]),
    fromProviders(classOf[RefreshToken]),
    fromProviders(classOf[UserT]),
    fromProviders(classOf[Credentials]),
    fromProviders(classOf[AlertT]),
    fromProviders(classOf[AlertsT]),
    fromProviders(classOf[TimeRange]),
    fromProviders(classOf[FacebookCredentialsT]),
    DEFAULT_CODEC_REGISTRY)


  lazy implicit val sandroidReceiptValidationRequestDecoder: Decoder[SubscriptionPurchase] = deriveDecoder[SubscriptionPurchase]

  implicit def sandroidReceiptValidationRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, SubscriptionPurchase] = jsonOf

  lazy implicit val sandroidReceiptValidationRequestEncoder: Encoder[SubscriptionPurchase] = deriveEncoder[SubscriptionPurchase]

  implicit def sandroidReceiptValidationRequestEntityEncoder[F[_] : Applicative]: EntityEncoder[F, SubscriptionPurchase] = jsonEncoderOf

  lazy implicit val beachIdDecoder: Decoder[BeachId] = deriveDecoder[BeachId]

  implicit def beachIdEntityDecoder[F[_] : Sync]: EntityDecoder[F, BeachId] = jsonOf

  lazy implicit val beachIdEncoder: Encoder[BeachId] = deriveEncoder[BeachId]

  implicit def beachIdEntityEncoder[F[_] : Applicative]: EntityEncoder[F, BeachId] = jsonEncoderOf

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


  lazy implicit val swellODecoder: Decoder[SwellOutput] = deriveDecoder[SwellOutput]

  implicit def swellOEntityDecoder[F[_] : Sync]: EntityDecoder[F, SwellOutput] = jsonOf

  lazy implicit val swellOEncoder: Encoder[SwellOutput] = deriveEncoder[SwellOutput]

  implicit def swellOEntityEncoder[F[_] : Applicative]: EntityEncoder[F, SwellOutput] = jsonEncoderOf


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

  lazy implicit val userDecoder: Decoder[UserDTO] = deriveDecoder[UserDTO]

  implicit def userEntityDecoder[F[_] : Sync]: EntityDecoder[F, UserDTO] = jsonOf

  lazy implicit val userEncoder: Encoder[UserDTO] = deriveEncoder[UserDTO]

  implicit def userEntityEncoder[F[_] : Applicative]: EntityEncoder[F, UserDTO] = jsonEncoderOf

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


  lazy implicit val srDecoder: Decoder[FacebookRegisterRequest] = deriveDecoder[FacebookRegisterRequest]

  implicit def srEntityDecoder[F[_] : Sync]: EntityDecoder[F, FacebookRegisterRequest] = jsonOf

  lazy implicit val srEncoder: Encoder[FacebookRegisterRequest] = deriveEncoder[FacebookRegisterRequest]

  implicit def srEntityEncoder[F[_] : Applicative]: EntityEncoder[F, FacebookRegisterRequest] = jsonEncoderOf

  lazy implicit val slDecoder: Decoder[FacebookLoginRequest] = deriveDecoder[FacebookLoginRequest]

  implicit def slEntityDecoder[F[_] : Sync]: EntityDecoder[F, FacebookLoginRequest] = jsonOf

  lazy implicit val slEncoder: Encoder[FacebookLoginRequest] = deriveEncoder[FacebookLoginRequest]

  implicit def slEntityEncoder[F[_] : Applicative]: EntityEncoder[F, FacebookLoginRequest] = jsonEncoderOf

  lazy implicit val rDecoder: Decoder[RegisterRequest] = deriveDecoder[RegisterRequest]

  implicit def rEntityDecoder[F[_] : Sync]: EntityDecoder[F, RegisterRequest] = jsonOf

  lazy implicit val rEncoder: Encoder[RegisterRequest] = deriveEncoder[RegisterRequest]

  implicit def rEntityEncoder[F[_] : Applicative]: EntityEncoder[F, RegisterRequest] = jsonEncoderOf

  lazy implicit val tokensDecoder: Decoder[TokensWithUser] = deriveDecoder[TokensWithUser]

  implicit def tokensEntityDecoder[F[_] : Sync]: EntityDecoder[F, TokensWithUser] = jsonOf

  lazy implicit val tokenEncoder: Encoder[TokensWithUser] = deriveEncoder[TokensWithUser]

  implicit def tokensEntityEncoder[F[_] : Applicative]: EntityEncoder[F, TokensWithUser] = jsonEncoderOf

  lazy implicit val accessTokenRequestDecoder: Decoder[AccessTokenRequest] = deriveDecoder[AccessTokenRequest]

  implicit def accessTokenRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, AccessTokenRequest] = jsonOf

  lazy implicit val accessTokenRequestEncoder: Encoder[AccessTokenRequest] = deriveEncoder[AccessTokenRequest]

  implicit def accessTokenRequestEntityEncoder[F[_] : Applicative]: EntityEncoder[F, AccessTokenRequest] = jsonEncoderOf

  lazy implicit val loginRequestDecoder: Decoder[LoginRequest] = deriveDecoder[LoginRequest]

  implicit def loginRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, LoginRequest] = jsonOf

  lazy implicit val loginRequestEncoder: Encoder[LoginRequest] = deriveEncoder[LoginRequest]

  implicit def loginRequestEntityEncoder[F[_] : Applicative]: EntityEncoder[F, LoginRequest] = jsonEncoderOf

  lazy implicit val changePasswordRequestDecoder: Decoder[ChangePasswordRequest] = deriveDecoder[ChangePasswordRequest]

  implicit def changePasswordRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, ChangePasswordRequest] = jsonOf

  lazy implicit val changePasswordRequestEncoder: Encoder[ChangePasswordRequest] = deriveEncoder[ChangePasswordRequest]

  implicit def changePasswordRequestEntityEncoder[F[_] : Applicative]: EntityEncoder[F, ChangePasswordRequest] = jsonEncoderOf

  lazy implicit val updateUserRequestDecoder: Decoder[UpdateUserRequest] = deriveDecoder[UpdateUserRequest]

  implicit def updateUserRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, UpdateUserRequest] = jsonOf

  lazy implicit val updateUserRequestEncoder: Encoder[UpdateUserRequest] = deriveEncoder[UpdateUserRequest]

  implicit def updateUserRequestEntityEncoder[F[_] : Applicative]: EntityEncoder[F, UpdateUserRequest] = jsonEncoderOf

  lazy implicit val otpDecoder: Decoder[OTP] = deriveDecoder[OTP]

  implicit def otpEntityDecoder[F[_] : Sync]: EntityDecoder[F, OTP] = jsonOf

  lazy implicit val otpEncoder: Encoder[OTP] = deriveEncoder[OTP]

  implicit def otpEncoder[F[_] : Applicative]: EntityEncoder[F, OTP] = jsonEncoderOf

  lazy implicit val appleReceiptValidationRequestDecoder: Decoder[AppleReceiptValidationRequest] = deriveDecoder[AppleReceiptValidationRequest]

  implicit def appleReceiptValidationRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, AppleReceiptValidationRequest] = jsonOf

  lazy implicit val appleReceiptValidationRequestEncoder: Encoder[AppleReceiptValidationRequest] = deriveEncoder[AppleReceiptValidationRequest]

  implicit def appleReceiptValidationRequestEncoder[F[_] : Applicative]: EntityEncoder[F, AppleReceiptValidationRequest] = jsonEncoderOf

  lazy implicit val androidReceiptValidationRequestDecoder: Decoder[AndroidReceiptValidationRequest] = deriveDecoder[AndroidReceiptValidationRequest]

  implicit def androidReceiptValidationRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, AndroidReceiptValidationRequest] = jsonOf

  lazy implicit val androidReceiptValidationRequestEncoder: Encoder[AndroidReceiptValidationRequest] = deriveEncoder[AndroidReceiptValidationRequest]

  implicit def androidReceiptValidationRequestEncoder[F[_] : Applicative]: EntityEncoder[F, AndroidReceiptValidationRequest] = jsonEncoderOf


}
