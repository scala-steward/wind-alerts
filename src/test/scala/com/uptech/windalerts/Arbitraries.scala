package com.uptech.windalerts

import com.uptech.windalerts.core.beaches.domain.{Swell, TideHeight, Wind}
import org.scalacheck._

import java.time.Instant


trait Arbitraries {
  implicit val wind = Arbitrary(Gen.resultOf(Wind))
  implicit val swell = Arbitrary(Gen.resultOf(Swell))
  implicit val tideHeight = Arbitrary(Gen.resultOf(TideHeight))
}
