package com.uptech.windalerts.infrastructure.endpoints

import com.uptech.windalerts.core.EventPublisher.Event
import com.uptech.windalerts.core.alerts.TimeRange
import com.uptech.windalerts.core.user.TokensWithUser
import com.uptech.windalerts.infrastructure.social.login.AccessRequests.{AppleAccessRequest, FacebookAccessRequest}
import io.scalaland.chimney.dsl._


object dtos {

  case class UpdateUserRequest(name: String, userType: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long)

  case class UpdateUserDeviceTokenRequest(deviceToken: String)

  case class AccessTokenRequest(refreshToken: String)

  case class TokensWithUserDTO(accessToken: String, refreshToken: String, expiredAt: Long, user: UserDTO)

  object TokensWithUserDTO {
    def fromDomain(tokenWithUser: TokensWithUser) = {
      tokenWithUser.into[TokensWithUserDTO].withFieldComputed(_.user, _.user.asDTO()).transform
    }
  }

  final case class OTP(otp: String)

  final case class UserDTO(id: String, email: String, name: String, deviceType: String, startTrialAt: Long, endTrialAt: Long, userType: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, lastPaymentAt: Long, nextPaymentAt: Long) {
    def this(id: String, email: String, name: String, deviceType: String, startTrialAt: Long, userType: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long) =
      this(id, email, name, deviceType, startTrialAt, if (startTrialAt == -1) -1L else (startTrialAt + (30L * 24L * 60L * 60L * 1000L)), userType, snoozeTill, disableAllAlerts, notificationsPerHour, -1, -1)
  }


  case class FacebookRegisterRequest(accessToken: String, deviceType: String, deviceToken: String) {
    def asDomain(): FacebookAccessRequest = {
      this.into[FacebookAccessRequest].transform
    }
  }

  case class AppleRegisterRequest(authorizationCode: String, nonce: String, deviceType: String, deviceToken: String, name: String) {
    def asDomain(): AppleAccessRequest = {
      this.into[AppleAccessRequest].transform
    }
  }

  case class RegisterRequest(email: String, name: String, password: String, deviceType: String, deviceToken: String)

  case class LoginRequest(email: String, password: String, deviceType: String, deviceToken: String)

  case class ChangePasswordRequest(email: String, oldPassword: String, newPassword: String, deviceType: String)

  case class ResetPasswordRequest(email: String, deviceType: String)

  final case class BeachIdDTO(id: Long) extends AnyVal

  final case class WindDTO(direction: Double = 0, speed: Double = 0, directionText: String, trend: String)

  final case class SwellDTO(height: Double = 0, direction: Double = 0, directionText: String)

  final case class TideHeightDTO(height: Double, status: String, nextLow: Long, nextHigh: Long)

  final case class SwellOutputDTO(height: Double = 0, direction: Double = 0, directionText: String)

  final case class TideDTO(height: TideHeightDTO, swell: SwellOutputDTO)

  final case class BeachDTO(beachId: BeachIdDTO, wind: WindDTO, tide: TideDTO)

  case class AlertRequest(
                           beachId: Long,
                           days: Seq[Long],
                           swellDirections: Seq[String],
                           timeRanges: Seq[TimeRange],
                           waveHeightFrom: Double,
                           waveHeightTo: Double,
                           windDirections: Seq[String],
                           tideHeightStatuses: Seq[String] = Seq("Rising", "Falling"),
                           enabled: Boolean,
                           timeZone: String = "Australia/Sydney")

  case class AlertsDTO(alerts: Seq[AlertDTO])

  case class AlertDTO(
                       id: String,
                       owner: String,
                       beachId: Long,
                       days: Seq[Long],
                       swellDirections: Seq[String],
                       timeRanges: Seq[TimeRange],
                       waveHeightFrom: Double,
                       waveHeightTo: Double,
                       windDirections: Seq[String],
                       tideHeightStatuses: Seq[String] = Seq("Rising", "Falling"),
                       enabled: Boolean,
                       timeZone: String = "Australia/Sydney") {
  }

  case class PurchaseReceiptValidationRequest(token: String)


  case class Message(data: String)

  case class AndroidUpdate(message: Message)

  case class SubscriptionNotificationWrapper(subscriptionNotification: SubscriptionNotification)

  case class SubscriptionNotification(purchaseToken: String)

  case class ApplePurchaseVerificationRequest(`receipt-data`: String, password: String, `exclude-old-transactions`: Boolean)

  case class AppleSubscriptionPurchase(product_id: String, purchase_date_ms: Long, expires_date_ms: Long)

  case class TokenResponse(access_token: String, id_token: String)

  case class AppleUser(sub: String, email: String)

  case class UserIdDTO(userId: String) extends AnyVal
  case class EmailId(email: String) extends AnyVal

  case class UserRegisteredUpdate(message:Message)

  case class UserRegisteredWrapper(userRegistered: UserRegistered)
  case class UserRegistered(userId: UserIdDTO, emailId: EmailId)

}