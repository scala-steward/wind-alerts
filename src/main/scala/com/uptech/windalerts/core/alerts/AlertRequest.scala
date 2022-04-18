package com.uptech.windalerts.core.alerts

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
