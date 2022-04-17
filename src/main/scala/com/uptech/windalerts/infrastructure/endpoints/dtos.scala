package com.uptech.windalerts.infrastructure.endpoints

import com.uptech.windalerts.core.alerts.TimeRange
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.infrastructure.social.login.AccessRequests.{AppleAccessRequest, FacebookAccessRequest}
import io.scalaland.chimney.dsl._


object dtos {

  case class UpdateUserRequest(name: String, userType: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long)

  case class UpdateUserDeviceTokenRequest(deviceToken: String)

  case class AccessTokenRequest(refreshToken: String)


  final case class OTP(otp: String)


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


  case class AlertsDTO(alerts: Seq[Alert])


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