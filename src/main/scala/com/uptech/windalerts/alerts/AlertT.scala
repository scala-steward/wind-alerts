package com.uptech.windalerts.alerts

import java.util.Calendar.{DAY_OF_WEEK, HOUR_OF_DAY, MINUTE}
import java.util.{Calendar, TimeZone}

import com.uptech.windalerts.domain.domain.{Alert, AlertRequest, Beach, TimeRange}
import io.scalaland.chimney.dsl._
import org.log4s.getLogger
import org.mongodb.scala.bson.ObjectId

object domain {
  private val logger = getLogger

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
            if (beach.tide.height.status.equals("Increasing")) "Rising" else "Falling"
          }))

    }

    def isToBeAlertedAt(minutes: Int): Boolean = timeRanges.exists(_.isWithinRange(minutes))
    def isToBeAlertedNow(): Boolean = {
      val cal = Calendar.getInstance(TimeZone.getTimeZone(timeZone))
      val day = adjustDay(cal.get(DAY_OF_WEEK))
      val minutes = cal.get(HOUR_OF_DAY) * 60 + cal.get(MINUTE)
      days.contains(day) && timeRanges.exists(_.isWithinRange(minutes))
    }

    def adjustDay(day : Int) = {
      if (day == 1)
        7
      else day -1
    }
    def isToBeAlertedAtMinutes(minutes: Int): Boolean = timeRanges.exists(_.isWithinRange(minutes))

    def asDTO(): Alert = {
      this.into[Alert].withFieldComputed(_.id, _._id.toHexString).transform
    }
  }

  object AlertT {
    def apply(owner: String, beachId: Long, days: Seq[Long], swellDirections: Seq[String], timeRanges: Seq[TimeRange], waveHeightFrom: Double, waveHeightTo: Double, windDirections: Seq[String], tideHeightStatuses: Seq[String], enabled: Boolean, timeZone: String): AlertT
    = new AlertT(new ObjectId(), owner, beachId, days, swellDirections, timeRanges, waveHeightFrom, waveHeightTo, windDirections, tideHeightStatuses, enabled, timeZone)

    def apply(alertRequest: AlertRequest, user: String): AlertT = {
      alertRequest.into[AlertT].withFieldComputed(_.owner, u => user).withFieldComputed(_._id, a => new ObjectId()).transform
    }
  }

}