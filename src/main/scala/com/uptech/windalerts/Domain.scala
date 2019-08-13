package com.uptech.windalerts

object Domain {

  final case class BeachId(id: Int) extends AnyVal

  case class BeachStatus(windStatus: WindStatus, swellStatus: SwellStatus, tideStatus: TideStatus)

  case class WindStatus(direction: Double = 0, speed: Double = 0)

  case class SwellStatus(height: Double = 0, direction: Double = 0)

  sealed trait TideStatus

  final case object High extends TideStatus

  final case object Low extends TideStatus
}
