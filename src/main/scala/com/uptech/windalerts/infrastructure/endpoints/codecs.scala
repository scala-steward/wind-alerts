package com.uptech.windalerts.infrastructure.endpoints

import cats.Applicative
import cats.effect.Sync
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.alerts.{Alerts, TimeRange}
import com.uptech.windalerts.core.beaches.domain._
import com.uptech.windalerts.core.credentials.{AppleCredentials, Credentials, FacebookCredentials}
import com.uptech.windalerts.core.notifications.Notification
import com.uptech.windalerts.core.otp.OTPWithExpiry
import com.uptech.windalerts.core.refresh.tokens.RefreshToken
import com.uptech.windalerts.core.social.subscriptions.{AndroidToken, AppleToken}
import com.uptech.windalerts.core.user.{TokensWithUser, UserT}
import dtos._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._

object codecs {
  val codecRegistry = fromRegistries(
    fromProviders(classOf[Notification]),
    fromProviders(classOf[OTPWithExpiry]),
    fromProviders(classOf[AndroidToken]),
    fromProviders(classOf[RefreshToken]),
    fromProviders(classOf[UserT]),
    fromProviders(classOf[Credentials]),
    fromProviders(classOf[Alert]),
    fromProviders(classOf[Alerts]),
    fromProviders(classOf[TimeRange]),
    fromProviders(classOf[FacebookCredentials]),
    fromProviders(classOf[AppleToken]),
    fromProviders(classOf[AppleCredentials]),
    DEFAULT_CODEC_REGISTRY)


  lazy implicit val beachIdDecoder: Decoder[BeachId] = deriveDecoder[BeachId]

  implicit def beachIdEntityDecoder[F[_] : Sync]: EntityDecoder[F, BeachId] = jsonOf

  lazy implicit val beachIdEncoder: Encoder[BeachId] = deriveEncoder[BeachId]

  implicit def beachIdEntityEncoder[F[_] : Applicative]: EntityEncoder[F, BeachId] = jsonEncoderOf


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

  lazy implicit val alertDecoder: Decoder[AlertDTO] = deriveDecoder[AlertDTO]

  implicit def alertEntityDecoder[F[_] : Sync]: EntityDecoder[F, AlertDTO] = jsonOf

  lazy implicit val alertEncoder: Encoder[AlertDTO] = deriveEncoder[AlertDTO]

  implicit def alertEntityEncoder[F[_] : Applicative]: EntityEncoder[F, AlertDTO] = jsonEncoderOf

  lazy implicit val timeRangeDecoder: Decoder[TimeRange] = deriveDecoder[TimeRange]

  implicit def timeRangeEntityDecoder[F[_] : Sync]: EntityDecoder[F, TimeRange] = jsonOf

  lazy implicit val timeRangeEncoder: Encoder[TimeRange] = deriveEncoder[TimeRange]

  implicit def timeRangeEntityEncoder[F[_] : Applicative]: EntityEncoder[F, TimeRange] = jsonEncoderOf

  lazy implicit val userDecoder: Decoder[UserDTO] = deriveDecoder[UserDTO]

  implicit def userEntityDecoder[F[_] : Sync]: EntityDecoder[F, UserDTO] = jsonOf

  lazy implicit val userEncoder: Encoder[UserDTO] = deriveEncoder[UserDTO]

  implicit def userEntityEncoder[F[_] : Applicative]: EntityEncoder[F, UserDTO] = jsonEncoderOf


  lazy implicit val alertRDecoder: Decoder[AlertRequest] = deriveDecoder[AlertRequest]

  implicit def alertRntityDecoder[F[_] : Sync]: EntityDecoder[F, AlertRequest] = jsonOf

  lazy implicit val alertREncoder: Encoder[AlertRequest] = deriveEncoder[AlertRequest]

  implicit def alertREntityEncoder[F[_] : Applicative]: EntityEncoder[F, AlertRequest] = jsonEncoderOf

  lazy implicit val salertDecoder: Decoder[AlertsDTO] = deriveDecoder[AlertsDTO]

  implicit def salertEntityDecoder[F[_] : Sync]: EntityDecoder[F, AlertsDTO] = jsonOf

  lazy implicit val salertEncoder: Encoder[AlertsDTO] = deriveEncoder[AlertsDTO]

  implicit def salertEntityEncoder[F[_] : Applicative]: EntityEncoder[F, AlertsDTO] = jsonEncoderOf


  lazy implicit val srDecoder: Decoder[FacebookRegisterRequest] = deriveDecoder[FacebookRegisterRequest]

  implicit def srEntityDecoder[F[_] : Sync]: EntityDecoder[F, FacebookRegisterRequest] = jsonOf

  lazy implicit val srEncoder: Encoder[FacebookRegisterRequest] = deriveEncoder[FacebookRegisterRequest]

  implicit def srEntityEncoder[F[_] : Applicative]: EntityEncoder[F, FacebookRegisterRequest] = jsonEncoderOf

  lazy implicit val rDecoder: Decoder[RegisterRequest] = deriveDecoder[RegisterRequest]

  implicit def rEntityDecoder[F[_] : Sync]: EntityDecoder[F, RegisterRequest] = jsonOf

  lazy implicit val rEncoder: Encoder[RegisterRequest] = deriveEncoder[RegisterRequest]

  implicit def rEntityEncoder[F[_] : Applicative]: EntityEncoder[F, RegisterRequest] = jsonEncoderOf

  lazy implicit val tokensDecoder: Decoder[TokensWithUserDTO] = deriveDecoder[TokensWithUserDTO]

  implicit def tokensEntityDecoder[F[_] : Sync]: EntityDecoder[F, TokensWithUserDTO] = jsonOf

  lazy implicit val tokenEncoder: Encoder[TokensWithUserDTO] = deriveEncoder[TokensWithUserDTO]

  implicit def tokensEntityEncoder[F[_] : Applicative]: EntityEncoder[F, TokensWithUserDTO] = jsonEncoderOf

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

  lazy implicit val resetPasswordRequestDecoder: Decoder[ResetPasswordRequest] = deriveDecoder[ResetPasswordRequest]

  implicit def resetPasswordRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, ResetPasswordRequest] = jsonOf

  lazy implicit val updateUserRequestDecoder: Decoder[UpdateUserRequest] = deriveDecoder[UpdateUserRequest]

  implicit def updateUserRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, UpdateUserRequest] = jsonOf

  lazy implicit val updateUserRequestEncoder: Encoder[UpdateUserRequest] = deriveEncoder[UpdateUserRequest]

  implicit def updateUserRequestEntityEncoder[F[_] : Applicative]: EntityEncoder[F, UpdateUserRequest] = jsonEncoderOf

  lazy implicit val updateUserDeviceTokenRequestDecoder: Decoder[UpdateUserDeviceTokenRequest] = deriveDecoder[UpdateUserDeviceTokenRequest]

  implicit def updateUserDeviceTokenRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, UpdateUserDeviceTokenRequest] = jsonOf

  lazy implicit val updateUserDeviceTokenRequestEncoder: Encoder[UpdateUserDeviceTokenRequest] = deriveEncoder[UpdateUserDeviceTokenRequest]

  implicit def updateUserDeviceTokenRequestEntityEncoder[F[_] : Applicative]: EntityEncoder[F, UpdateUserDeviceTokenRequest] = jsonEncoderOf

  lazy implicit val otpDecoder: Decoder[OTP] = deriveDecoder[OTP]

  implicit def otpEntityDecoder[F[_] : Sync]: EntityDecoder[F, OTP] = jsonOf

  lazy implicit val otpEncoder: Encoder[OTP] = deriveEncoder[OTP]

  implicit def otpEncoder[F[_] : Applicative]: EntityEncoder[F, OTP] = jsonEncoderOf

  lazy implicit val androidReceiptValidationRequestDecoder: Decoder[AndroidReceiptValidationRequest] = deriveDecoder[AndroidReceiptValidationRequest]

  implicit def androidReceiptValidationRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, AndroidReceiptValidationRequest] = jsonOf

  lazy implicit val androidReceiptValidationRequestEncoder: Encoder[AndroidReceiptValidationRequest] = deriveEncoder[AndroidReceiptValidationRequest]

  implicit def androidReceiptValidationRequestEncoder[F[_] : Applicative]: EntityEncoder[F, AndroidReceiptValidationRequest] = jsonEncoderOf

  lazy implicit val s1androidReceiptValidationRequestDecoder: Decoder[AndroidUpdate] = deriveDecoder[AndroidUpdate]

  implicit def s1androidReceiptValidationRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, AndroidUpdate] = jsonOf

  lazy implicit val s1androidReceiptValidationRequestEncoder: Encoder[AndroidUpdate] = deriveEncoder[AndroidUpdate]

  implicit def s1androidReceiptValidationRequestEncoder[F[_] : Applicative]: EntityEncoder[F, AndroidUpdate] = jsonEncoderOf

  lazy implicit val ms1androidReceiptValidationRequestDecoder: Decoder[Message] = deriveDecoder[Message]

  implicit def ms1androidReceiptValidationRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, Message] = jsonOf

  lazy implicit val ms1androidReceiptValidationRequestEncoder: Encoder[Message] = deriveEncoder[Message]

  implicit def ms1androidReceiptValidationRequestEncoder[F[_] : Applicative]: EntityEncoder[F, Message] = jsonEncoderOf


  lazy implicit val subscriptionNotificationDecoder: Decoder[SubscriptionNotification] = deriveDecoder[SubscriptionNotification]

  implicit def subscriptionNotificationEntityDecoder[F[_] : Sync]: EntityDecoder[F, SubscriptionNotification] = jsonOf

  lazy implicit val subscriptionNotificationEncoder: Encoder[SubscriptionNotification] = deriveEncoder[SubscriptionNotification]

  implicit def subscriptionNotificationEncoder[F[_] : Applicative]: EntityEncoder[F, SubscriptionNotification] = jsonEncoderOf


  lazy implicit val subscriptionNotificationWrapperDecoder: Decoder[SubscriptionNotificationWrapper] = deriveDecoder[SubscriptionNotificationWrapper]

  implicit def subscriptionNotificationWrapperEntityDecoder[F[_] : Sync]: EntityDecoder[F, SubscriptionNotificationWrapper] = jsonOf

  lazy implicit val subscriptionNotificationWrapperEncoder: Encoder[SubscriptionNotificationWrapper] = deriveEncoder[SubscriptionNotificationWrapper]

  implicit def subscriptionNotificationWrapperEncoder[F[_] : Applicative]: EntityEncoder[F, SubscriptionNotificationWrapper] = jsonEncoderOf


  lazy implicit val applePurchaseVerificationRequestDecoder: Decoder[ApplePurchaseVerificationRequest] = deriveDecoder[ApplePurchaseVerificationRequest]

  implicit def applePurchaseVerificationRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, ApplePurchaseVerificationRequest] = jsonOf

  lazy implicit val applePurchaseVerificationRequestEncoder: Encoder[ApplePurchaseVerificationRequest] = deriveEncoder[ApplePurchaseVerificationRequest]

  implicit def applePurchaseVerificationRequestEncoder[F[_] : Applicative]: EntityEncoder[F, ApplePurchaseVerificationRequest] = jsonEncoderOf


  lazy implicit val appleSubscriptionPurchaseDecoder: Decoder[AppleSubscriptionPurchase] = deriveDecoder[AppleSubscriptionPurchase]

  implicit def appleSubscriptionPurchaseEntityDecoder[F[_] : Sync]: EntityDecoder[F, AppleSubscriptionPurchase] = jsonOf

  lazy implicit val appleSubscriptionPurchaseEncoder: Encoder[AppleSubscriptionPurchase] = deriveEncoder[AppleSubscriptionPurchase]

  implicit def appleSubscriptionPurchaseEnityEncoder[F[_] : Applicative]: EntityEncoder[F, AppleSubscriptionPurchase] = jsonEncoderOf


  lazy implicit val applePurchaseTokenDecoder: Decoder[ApplePurchaseToken] = deriveDecoder[ApplePurchaseToken]

  implicit def applePurchaseTokenEntityDecoder[F[_] : Sync]: EntityDecoder[F, ApplePurchaseToken] = jsonOf

  lazy implicit val applePurchaseTokenEncoder: Encoder[ApplePurchaseToken] = deriveEncoder[ApplePurchaseToken]

  implicit def applePurchaseTokenEnityEncoder[F[_] : Applicative]: EntityEncoder[F, ApplePurchaseToken] = jsonEncoderOf


  lazy implicit val tokenResponseDecoder: Decoder[TokenResponse] = deriveDecoder[TokenResponse]

  implicit def tokenResponseEntityDecoder[F[_] : Sync]: EntityDecoder[F, TokenResponse] = jsonOf

  lazy implicit val tokenResponseEncoder: Encoder[TokenResponse] = deriveEncoder[TokenResponse]

  implicit def tokenResponseEnityEncoder[F[_] : Applicative]: EntityEncoder[F, TokenResponse] = jsonEncoderOf

  lazy implicit val appleRegisterRequestDecoder: Decoder[AppleRegisterRequest] = deriveDecoder[AppleRegisterRequest]

  implicit def appleRegisterRequestEntityDecoder[F[_] : Sync]: EntityDecoder[F, AppleRegisterRequest] = jsonOf

  lazy implicit val appleRegisterRequestEncoder: Encoder[AppleRegisterRequest] = deriveEncoder[AppleRegisterRequest]

  implicit def appleRegisterRequestEntityEncoder[F[_] : Applicative]: EntityEncoder[F, AppleRegisterRequest] = jsonEncoderOf

  lazy implicit val appleUserDecoder: Decoder[AppleUser] = deriveDecoder[AppleUser]

  implicit def appleUserEntityDecoder[F[_] : Sync]: EntityDecoder[F, AppleUser] = jsonOf

  lazy implicit val appleUserEncoder: Encoder[AppleUser] = deriveEncoder[AppleUser]

  implicit def appleUserEntityEncoder[F[_] : Applicative]: EntityEncoder[F, AppleUser] = jsonEncoderOf

  lazy implicit val userIdDTODecoder: Decoder[UserIdDTO] = deriveDecoder[UserIdDTO]
  implicit def  userIdDTOEntityDecoder[F[_] : Sync]: EntityDecoder[F, UserIdDTO] = jsonOf
  lazy implicit val  userIdDTOEncoder: Encoder[UserIdDTO] = deriveEncoder[UserIdDTO]
  implicit def  userIdDTOEntityEncoder[F[_] : Applicative]: EntityEncoder[F, UserIdDTO] = jsonEncoderOf

  lazy implicit val emailIddDecoder: Decoder[EmailId] = deriveDecoder[EmailId]
  implicit def emailIdEntityDecoder[F[_] : Sync]: EntityDecoder[F, EmailId] = jsonOf
  lazy implicit val emailIdEncoder: Encoder[EmailId] = deriveEncoder[EmailId]
  implicit def emailIdEntityEncoder[F[_] : Applicative]: EntityEncoder[F, EmailId] = jsonEncoderOf

  lazy implicit val userRegisteredDecoder: Decoder[UserRegistered] = deriveDecoder[UserRegistered]
  implicit def userRegisteredEntityDecoder[F[_] : Sync]: EntityDecoder[F, UserRegistered] = jsonOf
  lazy implicit val userRegisteredEncoder: Encoder[UserRegistered] = deriveEncoder[UserRegistered]
  implicit def userRegisteredEntityEncoder[F[_] : Applicative]: EntityEncoder[F, UserRegistered] = jsonEncoderOf


  lazy implicit val userRegisteredUpdateDecoder: Decoder[UserRegisteredUpdate] = deriveDecoder[UserRegisteredUpdate]
  implicit def userRegisteredEntityUpdateDecoder[F[_] : Sync]: EntityDecoder[F, UserRegisteredUpdate] = jsonOf


  lazy implicit val userRegisteredWrapperDecoder: Decoder[UserRegisteredWrapper] = deriveDecoder[UserRegisteredWrapper]
  implicit def userRegisteredWrapperEntityDecoder[F[_] : Sync]: EntityDecoder[F, UserRegisteredWrapper] = jsonOf

}