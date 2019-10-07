package com.uptech.windalerts.domain

import java.util

import scala.beans.BeanProperty
import scala.collection.JavaConverters
import scala.util.control.NonFatal

object domain {
  case class UserSettings(userId:String)
  case class Tokens(accessToken:String, refreshToken: String, expiredAt:Long)

  case class AccessTokenRequest(refreshToken: String)

  case class RefreshToken(refreshToken: String, expiry:Long, userId:String, accessTokenId:String) {
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

  case class Credentials(id:Option[String], email: String,  password: String, deviceType:String)

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

  final case class User(id: String, email: String, name: String, deviceId: String, deviceToken: String, deviceType: String)

  object User {
    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[User] = try {
      val values = tuple._2
      Some(User(
        tuple._1,
        values("email").asInstanceOf[String],
        values("name").asInstanceOf[String],
        values("deviceId").asInstanceOf[String],
        values("deviceToken").asInstanceOf[String],
        values("deviceType").asInstanceOf[String]
      ))
    }
    catch {
      case NonFatal(_) =>
        None
    }
  }

  final case class AlertWithUser(alert: Alert, user:User)

  final case class DeviceRequest(deviceId: String)

  final case class UserDevices(devices: Seq[UserDevice])

  final case class UserDevice(deviceId: String, ownerId: String)

  case class RegisterRequest(email: String, name: String, password: String, deviceId:String, deviceType:String, deviceToken:String)

  case class LoginRequest(email: String, password: String, deviceType: String, deviceToken:String)

  object UserDevice {
    def unapply(tuple: (String, Map[String, util.HashMap[String, String]])): Option[UserDevice] = try {
      val values = tuple._2
      Some(UserDevice(
        tuple._1,
        values("owner").asInstanceOf[String]
      ))
    }
    catch {
      case NonFatal(_) => None
    }
  }

  final case class BeachId(id: Int) extends AnyVal

  final case class Wind(direction: Double = 0, speed: Double = 0, directionText: String)

  final case class Swell(height: Double = 0, direction: Double = 0, directionText: String)

  final case class TideHeight(status: String)

  final case class Tide(height: TideHeight, swell: Swell)

  final case class Beach(wind: Wind, tide: Tide)

  case class TimeRange(@BeanProperty from: Int, @BeanProperty to: Int) {
    def isWithinRange(hour: Int): Boolean = from <= hour && to > hour
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
                    timeZone: String = "Australia/Sydney") {
    def isToBeNotified(beach: Beach): Boolean = {
      swellDirections.contains(beach.tide.swell.directionText) &&
        waveHeightFrom <= beach.tide.swell.height && waveHeightTo >= beach.tide.swell.height &&
        windDirections.contains(beach.wind.directionText)
    }

    def isToBeAlertedAt(hour: Int): Boolean = timeRanges.exists(_.isWithinRange(hour))
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
        values.get("timeZone").getOrElse("Australia/Sydney").asInstanceOf[String]
      ))
    }
    catch {
      case NonFatal(_) => None
    }

  }

  def j2s[A](inputList: util.List[A]): Seq[A] = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

  def j2sm[K, V](map: util.Map[K, V]): Map[K, V] = JavaConverters.mapAsScalaMap(map).toMap

}
