package com.uptech.windalerts.domain

import java.util

import cats.data.EitherT
import org.log4s.getLogger

import scala.beans.BeanProperty
import scala.collection.JavaConverters
import scala.util.control.NonFatal

object domain {
  type EitherX[F[_], T] = EitherT[F, String, T]

  private val logger = getLogger

  case class UpdateUserRequest(name: String, userType: String, snoozeTill: Long, notificationsPerHour : Long)

  case class UserId(id: String)

  case class UserSettings(userId: String)

  case class TokensWithUser(accessToken: String, refreshToken: String, expiredAt: Long, user: User)

  case class AccessTokenRequest(refreshToken: String)

  case class RefreshToken(refreshToken: String, expiry: Long, userId: String, accessTokenId: String) {
    def isExpired() = System.currentTimeMillis() > expiry
  }

  object RefreshToken {
    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[RefreshToken] = try {
      val values = tuple._2
      Some(RefreshToken(
        values("refreshToken").asInstanceOf[String],
        values("expiry").asInstanceOf[Long],
        values("userId").asInstanceOf[String],
        values("accessTokenId").asInstanceOf[String]
      ))
    }
    catch {
      case NonFatal(_) =>
        None
    }
  }

  case class FacebookCredentials(id: Option[String], email: String, accessToken: String, deviceType: String)

  object FacebookCredentials {
    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[Credentials] = try {
      val values = tuple._2
      Some(Credentials(
        Some(tuple._1),
        values("email").asInstanceOf[String],
        values("accessToken").asInstanceOf[String],
        values("deviceType").asInstanceOf[String]
      ))
    }
    catch {
      case NonFatal(_) =>
        None
    }
  }

  case class Credentials(id: Option[String], email: String, password: String, deviceType: String)

  object Credentials {
    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[Credentials] = try {
      val values = tuple._2
      Some(Credentials(
        Some(tuple._1),
        values("email").asInstanceOf[String],
        values("password").asInstanceOf[String],
        values("deviceType").asInstanceOf[String]
      ))
    }
    catch {
      case NonFatal(_) =>
        None
    }
  }

  sealed case class UserType(value: String)

  object UserType {

    object Registered extends UserType("Registered")

    object Trial extends UserType("Trial")

    object TrialExpired extends UserType("TrialExpired")

    object Premium extends UserType("Premium")

    val values = Seq(Registered, Trial, TrialExpired, Premium)

    def apply(value: String): UserType = value match {
      case Registered.value => Registered
      case Trial.value => Trial
      case TrialExpired.value => TrialExpired
      case Premium.value => Premium
    }
  }

  final case class OTP(otp: String)

  final case class OTPWithExpiry(otp: String, expiry: Long, userId: String)


  object OTPWithExpiry {
    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[OTPWithExpiry] = try {
      val values = tuple._2
      Some(OTPWithExpiry(
        values("otp").asInstanceOf[String],
        values("expiry").asInstanceOf[Long],
        values("userId").asInstanceOf[String]
      ))
    }
    catch {
      case NonFatal(_) =>
        None
    }
  }

  final case class User(id: String, email: String, name: String, deviceId: String, deviceToken: String, deviceType: String, startTrialAt: Long, userType: String, snoozeTill: Long, notificationsPerHour: Long) {
    def isTrialEnded() = {
      startTrialAt != -1 && startTrialAt < System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)
    }
  }

  object User {
    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[User] = try {
      val values = tuple._2
      Some(User(
        tuple._1,
        values("email").asInstanceOf[String],
        values("name").asInstanceOf[String],
        values("deviceId").asInstanceOf[String],
        values("deviceToken").asInstanceOf[String],
        values("deviceType").asInstanceOf[String],
        values("startTrialAt").asInstanceOf[Long],
        UserType(values("userType").asInstanceOf[String]).value,
        values("snoozeTill").asInstanceOf[Long],
        values("notificationsPerHour").asInstanceOf[Long]
      ))
    }
    catch {
      case NonFatal(_) =>
        None
    }
  }

  final case class AlertWithUser(alert: Alert, user: User)

  final case class DeviceRequest(deviceId: String)

  final case class UserDevices(devices: Seq[UserDevice])

  final case class UserDevice(deviceId: String, ownerId: String)

  case class FacebookRegisterRequest(accessToken: String, deviceId: String, deviceType: String, deviceToken: String)

  case class RegisterRequest(email: String, name: String, password: String, deviceId: String, deviceType: String, deviceToken: String)

  case class FacebookLoginRequest(accessToken: String, deviceType: String, deviceToken: String)

  case class LoginRequest(email: String, password: String, deviceType: String, deviceToken: String)

  case class ChangePasswordRequest(email: String, oldPassword: String, newPassword: String, deviceType: String)

  final case class BeachId(id: Int) extends AnyVal

  final case class Wind(direction: Double = 0, speed: Double = 0, directionText: String)

  final case class Swell(height: Double = 0, direction: Double = 0, directionText: String)

  final case class TideHeight(height: Double, status: String, nextLow: Long, nextHigh: Long)

  final case class SwellOutput(height: Double = 0, direction: Double = 0, directionText: String)

  final case class Tide(height: TideHeight, swell: SwellOutput)

  final case class Beach(wind: Wind, tide: Tide)

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

  sealed case class TideHeightStatus(value: String)

  object TideHeightStatus {

    object Rising extends TideHeightStatus("Rising")

    object Falling extends TideHeightStatus("Falling")

    val values = Seq(Rising, Falling)

    def apply(value: String): TideHeightStatus = value match {
      case Rising.value => Rising
      case Falling.value => Falling
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
    def isToBeNotified(beach: Beach): Boolean = {
      logger.error(s"beach to check $beach")
      logger.error(s"self $swellDirections $waveHeightFrom $waveHeightTo $windDirections")

      swellDirections.contains(beach.tide.swell.directionText) &&
        waveHeightFrom <= beach.tide.swell.height && waveHeightTo >= beach.tide.swell.height &&
        windDirections.contains(beach.wind.directionText) &&
        tideHeightStatuses.contains(beach.tide.height.status)
    }

    def isToBeAlertedAt(minutes: Int): Boolean = timeRanges.exists(_.isWithinRange(minutes))
  }

  object Alert {

    def apply(alertRequest: AlertRequest, user: String): Alert =
      new Alert(
        "",
        user,
        beachId = alertRequest.beachId,
        days = alertRequest.days,
        swellDirections = alertRequest.swellDirections,
        timeRanges = alertRequest.timeRanges,
        waveHeightFrom = alertRequest.waveHeightFrom,
        waveHeightTo = alertRequest.waveHeightTo,
        windDirections = alertRequest.windDirections,
        tideHeightStatuses = alertRequest.tideHeightStatuses,
        enabled = alertRequest.enabled,
        timeZone = alertRequest.timeZone)

    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[Alert] = try {
      val values = tuple._2
      println(values)
      Some(Alert(
        tuple._1,
        values("owner").asInstanceOf[String],
        values("beachId").asInstanceOf[Long],
        j2s(values("days").asInstanceOf[util.ArrayList[Long]]),
        j2s(values("swellDirections").asInstanceOf[util.ArrayList[String]]),
        {
          val ranges = j2s(values("timeRanges").asInstanceOf[util.ArrayList[util.HashMap[String, Long]]]).map(p => j2sm(p))
            .map(r => {
              val TimeRange(tr) = r
              tr
            })
          ranges
        },
        values("waveHeightFrom").asInstanceOf[Number].doubleValue(),
        values("waveHeightTo").asInstanceOf[Number].doubleValue(),
        j2s(values("windDirections").asInstanceOf[util.ArrayList[String]]),
        j2s(values("tideHeightStatuses").asInstanceOf[util.ArrayList[String]]),
        values("enabled").asInstanceOf[Boolean],
        values.get("timeZone").getOrElse("Australia/Sydney").asInstanceOf[String]
      ))
    }
    catch {
      case NonFatal(_) => None
    }

  }

  case class Notification(id: Option[String], alertId: String, userId: String, deviceToken: String, title: String, body: String, sentAt: Long)

  object Notification {
    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[Notification] = try {
      val values = tuple._2
      Some(Notification(
        Some(tuple._1),
        values("alertId").asInstanceOf[String],
        values("userId").asInstanceOf[String],
        values("deviceToken").asInstanceOf[String],
        values("title").asInstanceOf[String],
        values("body").asInstanceOf[String],
        values("sentAt").asInstanceOf[Long]
      ))
    }
    catch {
      case NonFatal(_) =>
        None
    }
  }

  def j2s[A](inputList: util.List[A]): Seq[A] = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

  def j2sm[K, V](map: util.Map[K, V]): Map[K, V] = JavaConverters.mapAsScalaMap(map).toMap

  case class AppleReceiptValidationRequest(`receipt-data`:String, password:String)
}
