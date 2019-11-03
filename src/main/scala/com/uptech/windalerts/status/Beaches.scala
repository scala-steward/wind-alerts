package com.uptech.windalerts.status

import cats.effect.IO
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.{Beach, BeachId, SwellOutput, Tide, TideHeight, TideHeightOutput}


trait Beaches extends Serializable {
  val beaches: Beaches.Service
}

object Beaches {

  trait Service extends Serializable {
    def get(beachId: BeachId): IO[domain.Beach]
  }

  final case class ServiceImpl[R](W: Winds.Service, S: Swells.Service, T: Tides.Service) extends Service {
    override def get(beachId: BeachId): IO[Beach] = {
      for {
        wind <- W.get(beachId)
        tide <- T.get(beachId)
        swell <- S.get(beachId)
      } yield Beach(wind, Tide(TideHeightOutput(tide.status), SwellOutput(tide.height, swell.direction, swell.directionText, tide.nextLow, tide.nextHigh)))
    }
  }

}