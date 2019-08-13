package com.uptech.windalerts

object Domain {
  final case class BeachId(id: Int) extends AnyVal
  case class WindStatus(direction: Double = 0, speed: Double = 0)
  case class SwellStatus(height: Double = 0, direction: Double = 0)
  case class TideHeightStatus(status: String)
  case class TideStatus(tideHeightStatus: TideHeightStatus, swellStatus: SwellStatus)
  case class BeachStatus(windStatus: WindStatus, tideStatus: TideStatus)
}
