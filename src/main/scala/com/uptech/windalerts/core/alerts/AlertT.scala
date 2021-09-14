package com.uptech.windalerts.core.alerts

import com.uptech.windalerts.core.beaches.domain.Beach

import java.util.Calendar.{DAY_OF_WEEK, HOUR_OF_DAY, MINUTE}
import java.util.{Calendar, TimeZone}
import com.uptech.windalerts.infrastructure.endpoints.dtos.{AlertDTO, AlertRequest}
import io.scalaland.chimney.dsl._
import org.log4s.getLogger
import org.mongodb.scala.bson.ObjectId

object domain {
  private val logger = getLogger

  case class Alert(
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
                     timeZone: String = "Australia/Sydney",
                     createdAt: Long) {
    def isToBeNotified(beachStatus: Beach): Boolean = {
      logger.error(s"beach to check $beachStatus")
      logger.error(s"self $swellDirections $waveHeightFrom $waveHeightTo $windDirections")

      swellDirections.contains(beachStatus.tide.swell.directionText) &&
        waveHeightFrom <= beachStatus.tide.swell.height && waveHeightTo >= beachStatus.tide.swell.height &&
        windDirections.contains(beachStatus.wind.directionText) &&
        (tideHeightStatuses.contains(beachStatus.tide.height.status) || tideHeightStatuses.contains(
          {
            if (beachStatus.tide.height.status.equals("Increasing")) "Rising" else "Falling"
          }))

    }

    def isToBeAlertedAt(minutes: Int): Boolean = timeRanges.exists(_.isWithinRange(minutes))

    def isToBeAlertedNow(): Boolean = {
      val cal = Calendar.getInstance(TimeZone.getTimeZone(timeZone))
      val day = adjustDay(cal.get(DAY_OF_WEEK))
      val minutes = cal.get(HOUR_OF_DAY) * 60 + cal.get(MINUTE)
      days.contains(day) && timeRanges.exists(_.isWithinRange(minutes))
    }

    def adjustDay(day: Int) = {
      if (day == 1)
        7
      else day - 1
    }

    def isToBeAlertedAtMinutes(minutes: Int): Boolean = timeRanges.exists(_.isWithinRange(minutes))

    def asDTO(): AlertDTO = {
      this.into[AlertDTO].withFieldComputed(_.id, _._id.toHexString).transform
    }

    def allFieldExceptStatusAreSame(alertRequest: AlertRequest) = {
      days.sorted == alertRequest.days.sorted &&
        swellDirections.sorted == alertRequest.swellDirections.sorted &&
        timeRanges.sortBy(_.from) == alertRequest.timeRanges.sortBy(_.from) &&
        waveHeightFrom == alertRequest.waveHeightFrom &&
        waveHeightTo == alertRequest.waveHeightTo &&
        windDirections.sorted == alertRequest.windDirections.sorted &&
        tideHeightStatuses.sorted == alertRequest.tideHeightStatuses.sorted &&
        timeZone == alertRequest.timeZone
    }
  }

  object Alert {
    def apply(alertRequest: AlertRequest, user: String): Alert = {
      alertRequest.into[Alert].withFieldComputed(_.owner, u => user).withFieldComputed(_._id, a => new ObjectId()).withFieldComputed(_.createdAt, _=>System.currentTimeMillis()).transform
    }
  }

}