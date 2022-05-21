package com.uptech.windalerts.core.alerts

import com.uptech.windalerts.core.beaches.domain.Beach
import com.uptech.windalerts.core.social.subscriptions.PurchaseToken
import com.uptech.windalerts.logger

import java.util.Calendar.{DAY_OF_WEEK, HOUR_OF_DAY, MINUTE}
import java.util.{Calendar, TimeZone}
import io.scalaland.chimney.dsl._

object domain {
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
                    timeZone: String = "Australia/Sydney",
                    createdAt: Long) {
    def isToBeNotified(beachStatus: Beach): Boolean = {
      logger.info(s"beach to check $beachStatus")
      logger.info(s"self $swellDirections $waveHeightFrom $waveHeightTo $windDirections")
      swellDirections.contains(beachStatus.tide.swell.directionText) &&
        waveHeightFrom <= beachStatus.tide.swell.height && waveHeightTo >= beachStatus.tide.swell.height &&
        windDirections.contains(beachStatus.wind.directionText) &&
        (tideHeightStatuses.contains(beachStatus.tide.height.status) || tideHeightStatuses.contains(
          {
            if (beachStatus.tide.height.status.equals("Increasing")) "Rising" else "Falling"
          }))
    }

    def isTimeMatch(): Boolean = {
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

    def allFieldExceptStatusAreSame(alertRequest: AlertRequest) = {
      toAlertRequest().copy(enabled = alertRequest.enabled) == alertRequest
    }

    def toAlertRequest():AlertRequest = {
      this.into[AlertRequest].transform
    }
  }
}