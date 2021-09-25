package com.uptech.windalerts

import cats.data.EitherT
import cats.effect.IO
import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.core.beaches.domain._
import com.uptech.windalerts.core.beaches._
import com.uptech.windalerts.infrastructure.endpoints.codecs._
import org.http4s.Uri
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class BeachesEndpointsSpec extends AnyFunSuite
  with Matchers
  with ScalaCheckPropertyChecks
  with Arbitraries
  with Http4sDsl[IO]
  with Http4sClientDsl[IO] {

  test("fetch wind swell and tide status") {
    forAll { (w: Wind, t: TideHeight, s: Swell) =>
      (
        for {
          request <- GET(Uri.unsafeFromString(s"1/currentStatus"))
          response <- beachEndPoints(w, t, s).orNotFound.run(request)
          status <- response.as[Beach]
        } yield status shouldBe Beach(BeachId(1), w, Tide(t, SwellOutput(s.height, s.direction, s.directionText)))
        ).unsafeRunSync()
    }
  }

  private def beachEndPoints(w: Wind, t: TideHeight, s: Swell) = {
    new com.uptech.windalerts.infrastructure.endpoints.BeachesEndpoints[IO](new BeachService[IO](new FixedWindService(w), new FixedTidesService(t), new FixedSwellService(s))).allRoutes()
  }

  class FixedWindService(wind: Wind) extends WindsService[IO] {
    override def get(beachId: domain.BeachId): EitherT[IO, SurfsUpError, domain.Wind] = EitherT.pure(wind)
  }

  class FixedTidesService(tideHeight: TideHeight) extends TidesService[IO] {
    override def get(beachId: domain.BeachId): EitherT[IO, SurfsUpError, domain.TideHeight] = EitherT.pure(tideHeight)
  }

  class FixedSwellService(swell: Swell) extends SwellsService[IO] {
    override def get(beachId: domain.BeachId): EitherT[IO, SurfsUpError, domain.Swell] = EitherT.pure(swell)
  }
}