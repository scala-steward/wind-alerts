package com.uptech.windalerts.core.alerts

import com.uptech.windalerts.core.alerts.domain.Alert
import io.scalaland.chimney.dsl.TransformerOps
import org.scalatest.flatspec.AnyFlatSpec

class AlertSpec extends AnyFlatSpec {
  val testAlert: Alert = Alert(
    "id",
    "owner",
    1,
    Seq.empty,
    Seq.empty,
    Seq.empty,
    0,
    0,
    Seq.empty,
    Seq.empty,
    true,
    "",
    0
  )
  val alertRequest = testAlert.into[AlertRequest].transform


  it should "return true when all fields same" in {
    assert(testAlert.allFieldExceptStatusAreSame(alertRequest) === true)
  }
  it should "return true status is different" in {
    assert(testAlert.copy(enabled = true).allFieldExceptStatusAreSame(alertRequest.copy(enabled = false)) === true)
    assert(testAlert.copy(enabled = false).allFieldExceptStatusAreSame(alertRequest.copy(enabled = true)) === true)
  }
  it should "return false when beachId is different" in {
    assert(testAlert.allFieldExceptStatusAreSame(alertRequest.copy(beachId = 2)) === false)
  }
  it should "return false when days is different" in {
    assert(testAlert.allFieldExceptStatusAreSame(alertRequest.copy(days = Seq(1, 2, 3))) === false)
  }
  it should "return false when swellDirections is different" in {
    assert(testAlert.allFieldExceptStatusAreSame(alertRequest.copy(swellDirections = Seq("N"))) === false)
  }
  it should "return false when timeRanges is different" in {
    assert(testAlert.allFieldExceptStatusAreSame(alertRequest.copy(timeRanges = Seq(TimeRange(1, 2)))) === false)
  }
  it should "return false when waveHeightFrom is different" in {
    assert(testAlert.allFieldExceptStatusAreSame(alertRequest.copy(waveHeightFrom = 1)) === false)
  }
  it should "return false when waveHeightTo is different" in {
    assert(testAlert.allFieldExceptStatusAreSame(alertRequest.copy(waveHeightTo = 1)) === false)
  }
  it should "return false when windDirections is different" in {
    assert(testAlert.allFieldExceptStatusAreSame(alertRequest.copy(windDirections = Seq("N"))) === false)
  }
  it should "return false when tideHeightStatuses is different" in {
    assert(testAlert.allFieldExceptStatusAreSame(alertRequest.copy(tideHeightStatuses = Seq("Rising"))) === false)
  }
  it should "return false when timeZone is different" in {
    assert(testAlert.allFieldExceptStatusAreSame(alertRequest.copy(tideHeightStatuses = Seq("UTC"))) === false)
  }

}

