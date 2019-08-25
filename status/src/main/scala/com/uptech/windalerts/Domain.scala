package com.uptech.windalerts

object Domain {
  final case class BeachId(id: Int) extends AnyVal
  case class Wind(direction: Double = 0, speed: Double = 0)
  case class Swell(height: Double = 0, direction: Double = 0)
  case class TideHeight(status: String)
  case class Tide(height: TideHeight, swell: Swell)
  case class Beach(wind: Wind, tide: Tide)
}
