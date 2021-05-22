package com.uptech.windalerts.core.beaches

object domain {

  final case class BeachId(id: Long) extends AnyVal

  final case class Wind(direction: Double = 0, speed: Double = 0, directionText: String, trend: String)

  final case class Swell(height: Double = 0, direction: Double = 0, directionText: String)

  final case class TideHeight(height: Double, status: String, nextLow: Long, nextHigh: Long)

  final case class SwellOutput(height: Double = 0, direction: Double = 0, directionText: String)

  final case class Tide(height: TideHeight, swell: SwellOutput)

  final case class Beach(beachId: BeachId, wind: Wind, tide: Tide)

}
