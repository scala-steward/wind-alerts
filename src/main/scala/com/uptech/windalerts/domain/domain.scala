package com.uptech.windalerts.domain

import java.util

import com.google.api.services.androidpublisher.model.{IntroductoryPriceInfo, SubscriptionCancelSurveyResult, SubscriptionPriceChange}
import io.circe.generic.JsonCodec
import org.log4s.getLogger
import org.mongodb.scala.bson.ObjectId

import scala.beans.BeanProperty
import scala.collection.JavaConverters
import scala.util.control.NonFatal
import io.scalaland.chimney.dsl._
import io.circe._, io.circe.generic.semiauto._


object domain {

  private val logger = getLogger

  case class UpdateUserRequest(name: String, userType: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long)

  case class UserId(id: String)

  case class UserSettings(userId: String)

  case class TokensWithUser(accessToken: String, refreshToken: String, expiredAt: Long, user: UserDTO)


  case class SubscriptionPurchase(startTimeMillis: Long,
                                  expiryTimeMillis: Long)

  case class AccessTokenRequest(refreshToken: String)

  case class RefreshToken(_id: ObjectId, refreshToken: String, expiry: Long, userId: String, accessTokenId: String) {
    def isExpired() = System.currentTimeMillis() > expiry
  }

  object RefreshToken {
    def apply(refreshToken: String, expiry: Long, userId: String, accessTokenId: String): RefreshToken = new RefreshToken(new ObjectId(), refreshToken, expiry, userId, accessTokenId)
  }

  case class FacebookCredentialsT(_id: ObjectId, email: String, accessToken: String, deviceType: String)

  object FacebookCredentialsT {
    def apply(email: String, accessToken: String, deviceType: String): FacebookCredentialsT = new FacebookCredentialsT(new ObjectId(), email, accessToken, deviceType)
  }

  case class AppleCredentials(_id: ObjectId, email: String, deviceType: String, appleId: String)

  object AppleCredentials {
    def apply(email: String, deviceType: String, appleId: String): AppleCredentials = new AppleCredentials(new ObjectId(), email, deviceType, appleId)
  }

  case class Credentials(_id: ObjectId, email: String, password: String, deviceType: String)

  object Credentials {
    def apply(email: String, password: String, deviceType: String): Credentials = new Credentials(new ObjectId(), email, password, deviceType)
  }


  sealed case class UserType(value: String)

  object UserType {

    object Registered extends UserType("Registered")

    object PremiumExpired extends UserType("PremiumExpired")

    object Trial extends UserType("Trial")

    object TrialExpired extends UserType("TrialExpired")

    object Premium extends UserType("Premium")

    val values = Seq(Registered, PremiumExpired, Trial, TrialExpired, Premium)

    def apply(value: String): UserType = value match {
      case Registered.value => Registered
      case PremiumExpired.value => PremiumExpired
      case Trial.value => Trial
      case TrialExpired.value => TrialExpired
      case Premium.value => Premium
    }
  }

  final case class OTP(otp: String)

  case class OTPWithExpiry(_id: ObjectId, otp: String, expiry: Long, userId: String)

  object OTPWithExpiry {
    def apply(otp: String, expiry: Long, userId: String): OTPWithExpiry = new OTPWithExpiry(new ObjectId(), otp, expiry, userId)
  }

  final case class UserDTO(id: String, email: String, name: String, deviceId: String, deviceToken: String, deviceType: String, startTrialAt: Long, endTrialAt: Long, userType: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, lastPaymentAt: Long, nextPaymentAt: Long) {
    def this(id: String, email: String, name: String, deviceId: String, deviceToken: String, deviceType: String, startTrialAt: Long, userType: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long) =
      this(id, email, name, deviceId, deviceToken, deviceType, startTrialAt, if (startTrialAt == -1) -1L else (startTrialAt + (30L * 24L * 60L * 60L * 1000L)), userType, snoozeTill, disableAllAlerts, notificationsPerHour, -1, -1)

  }


  case class UserT(_id: ObjectId, email: String, name: String, deviceId: String, deviceToken: String, deviceType: String, startTrialAt: Long, endTrialAt: Long, userType: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, lastPaymentAt: Long, nextPaymentAt: Long) {
    def isTrialEnded() = {
      startTrialAt != -1 && endTrialAt < System.currentTimeMillis()
    }
  }

  object UserT {
    def create(_id: ObjectId, email: String, name: String, deviceId: String, deviceToken: String, deviceType: String, startTrialAt: Long, userType: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long) =
      UserT(_id, email, name, deviceId, deviceToken, deviceType, startTrialAt, if (startTrialAt == -1) -1L else (startTrialAt + (30L * 24L * 60L * 60L * 1000L)), userType, snoozeTill, disableAllAlerts, notificationsPerHour, -1, -1)

    def apply(email: String, name: String, deviceId: String, deviceToken: String, deviceType: String, startTrialAt: Long, endTrialAt: Long, userType: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, lastPaymentAt: Long, nextPaymentAt: Long): UserT = new UserT(new ObjectId(), email, name, deviceId, deviceToken, deviceType, startTrialAt, endTrialAt, userType, snoozeTill, disableAllAlerts, notificationsPerHour, lastPaymentAt, nextPaymentAt)
  }

  final case class AlertWithUser(alert: Alert, user: UserT)

  final case class AlertWithBeach(alert: AlertT, beach: domain.Beach)

  final case class AlertWithUserWithBeach(alert: AlertT, user: UserT, beach: domain.Beach)

  final case class UserWithCount(userId: String, count: Int)

  final case class DeviceRequest(deviceId: String)

  final case class UserDevices(devices: Seq[UserDevice])

  final case class UserDevice(deviceId: String, ownerId: String)

  case class FacebookRegisterRequest(accessToken: String, deviceId: String, deviceType: String, deviceToken: String)

  case class AppleRegisterRequest(authorizationCode: String, nonce: String, deviceId: String, deviceType: String, deviceToken: String, name:String)

  case class AppleLoginRequest(authorizationCode: String, nonce: String, deviceId: String, deviceType: String, deviceToken: String)

  case class RegisterRequest(email: String, name: String, password: String, deviceId: String, deviceType: String, deviceToken: String)

  case class FacebookLoginRequest(accessToken: String, deviceType: String, deviceToken: String)

  case class LoginRequest(email: String, password: String, deviceType: String, deviceToken: String)

  case class ChangePasswordRequest(email: String, oldPassword: String, newPassword: String, deviceType: String)

  case class ResetPasswordRequest(email: String, deviceType: String)

  final case class BeachId(id: Long) extends AnyVal

  final case class Wind(direction: Double = 0, speed: Double = 0, directionText: String)

  final case class Swell(height: Double = 0, direction: Double = 0, directionText: String)

  final case class TideHeight(height: Double, status: String, nextLow: Long, nextHigh: Long)

  final case class SwellOutput(height: Double = 0, direction: Double = 0, directionText: String)

  final case class Tide(height: TideHeight, swell: SwellOutput)

  final case class Beach(beachId: BeachId, wind: Wind, tide: Tide)

  case class TimeRange(@BeanProperty from: Int, @BeanProperty to: Int) {
    def isWithinRange(hourAndMinutes: Int): Boolean = from <= hourAndMinutes && to > hourAndMinutes
  }

  object TimeRange {
    def unapply(values: Map[String, Long]): Option[TimeRange] = try {
      Some(new TimeRange(values("from").toInt, values("to").toInt))
    }
    catch {
      case NonFatal(_) => None
    }
  }


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

  case class Alerts(alerts: Seq[Alert])

  case class AlertsT(alerts: Seq[AlertT])

  case class AlertT(
                     _id: ObjectId,
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
    def isToBeNotified(beach: Beach): Boolean = {
      logger.error(s"beach to check $beach")
      logger.error(s"self $swellDirections $waveHeightFrom $waveHeightTo $windDirections")

      swellDirections.contains(beach.tide.swell.directionText) &&
        waveHeightFrom <= beach.tide.swell.height && waveHeightTo >= beach.tide.swell.height &&
        windDirections.contains(beach.wind.directionText) &&
        (tideHeightStatuses.contains(beach.tide.height.status) || tideHeightStatuses.contains(
          {
            if (beach.tide.height.status.equals("Increasing"))"Rising" else "Falling"
          }))

    }

    def isToBeAlertedAt(minutes: Int): Boolean = timeRanges.exists(_.isWithinRange(minutes))
  }

  object AlertT {
    def apply(owner: String, beachId: Long, days: Seq[Long], swellDirections: Seq[String], timeRanges: Seq[TimeRange], waveHeightFrom: Double, waveHeightTo: Double, windDirections: Seq[String], tideHeightStatuses: Seq[String], enabled: Boolean, timeZone: String): AlertT
    = new AlertT(new ObjectId(), owner, beachId, days, swellDirections, timeRanges, waveHeightFrom, waveHeightTo, windDirections, tideHeightStatuses, enabled, timeZone)

    def apply(alertRequest: AlertRequest, user: String): AlertT = {
      alertRequest.into[AlertT].withFieldComputed(_.owner, u => user).withFieldComputed(_._id, a => new ObjectId()).transform
    }
  }

  case class Alert(
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

  case class Notification(_id: ObjectId, alertId: String, userId: String, deviceToken: String, title: String, body: String, sentAt: Long)

  object Notification {
    def apply(alertId: String, userId: String, deviceToken: String, title: String, body: String, sentAt: Long): Notification
    = new Notification(new ObjectId(), alertId, userId, deviceToken, title, body, sentAt)
  }

  def j2s[A](inputList: util.List[A]): Seq[A] = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

  def j2sm[K, V](map: util.Map[K, V]): Map[K, V] = JavaConverters.mapAsScalaMap(map).toMap

  case class AppleReceiptValidationRequest(`receipt-data`: String, password: String)

  case class AndroidReceiptValidationRequest(productId: String, token: String)

  case class ApplePurchaseToken(token: String)

  case class AndroidPurchase(_id: ObjectId,
                             userId: String,
                             acknowledgementState: Int,
                             consumptionState: Int,
                             developerPayload: String,
                             kind: String,
                             orderId: String,
                             purchaseState: Int,
                             purchaseTimeMillis: Long,
                             purchaseType: Int
                            )

  object AndroidPurchase {
    def apply(userId: String, acknowledgementState: Int, consumptionState: Int, developerPayload: String, kind: String, orderId: String, purchaseState: Int, purchaseTimeMillis: Long, purchaseType: Int): AndroidPurchase
    = new AndroidPurchase(new ObjectId(), userId, acknowledgementState, consumptionState, developerPayload, kind, orderId, purchaseState, purchaseTimeMillis, purchaseType)
  }

  case class AndroidToken(_id: ObjectId,
                          userId: String,
                          subscriptionId: String,
                          purchaseToken: String,
                          creationTime: Long
                         )

  object AndroidToken {
    def apply(userId: String, subscriptionId: String, purchaseToken: String, creationTime: Long): AndroidToken = new AndroidToken(new ObjectId(), userId, subscriptionId, purchaseToken, creationTime)
  }

  case class AndroidUpdate(message: Message)

  case class Message(data: String)

  case class SubscriptionNotificationWrapper(subscriptionNotification: SubscriptionNotification)

  case class SubscriptionNotification(purchaseToken: String)


  case class ApplePurchaseVerificationRequest(`receipt-data`: String, password: String, `exclude-old-transactions`: Boolean)

  case class AppleSubscriptionPurchase(product_id: String, purchase_date_ms: Long, expires_date_ms: Long)

  case class AppleToken(_id: ObjectId,
                        userId: String,
                        purchaseToken: String,
                        creationTime: Long
                       )

  object AppleToken {
    def apply(userId: String, purchaseToken: String, creationTime: Long): AppleToken = new AppleToken(new ObjectId(), userId, purchaseToken, creationTime)
  }

  case class ApplePublicKeyList(keys: Seq[ApplePublicKey])

  case class ApplePublicKey(
                             kty: String,
                             kid: String,
                             use: String,
                             alg: String,
                             n: String,
                             e: String)

  case class TokenResponse(access_token: String, id_token: String)

  case class AppleUser(sub: String, email: String)

  case class Feedback(_id: ObjectId, topic: String, message: String, userId:String)
  object Feedback {
    def apply(topic: String, message: String, userId: String): Feedback = new Feedback(new ObjectId, topic, message, userId)
  }

  case class FeedbackRequest(topic: String, message: String)

}