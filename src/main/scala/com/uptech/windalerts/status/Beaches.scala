package com.uptech.windalerts.status

import cats.effect.IO
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.{Beach, BeachId, SwellOutput, Tide}


trait Beaches extends Serializable {
  val beaches: Beaches.Service
}

object Beaches {

  trait Service extends Serializable {
    def get(beachId: BeachId): IO[domain.Beach]
  }

  final case class ServiceImpl[R](W: Winds.Service, S: Swells.Service, T: Tides.Service) extends Service {
    override def get(beachId: BeachId): IO[Beach] = for {
      _ <- IO(com.uptech.windalerts.domain.commons.tracer.getCurrentSpan.addAnnotation("Getting wind data"))
      wind <- W.get(beachId)
      _ <- IO(com.uptech.windalerts.domain.commons.tracer.getCurrentSpan.addAnnotation("Getting tide data"))
      tide <- T.get(beachId)
      _ <- IO(com.uptech.windalerts.domain.commons.tracer.getCurrentSpan.addAnnotation("Getting swell data"))
      swell <- S.get(beachId)
    } yield Beach(wind, Tide(tide, SwellOutput(swell.height, swell.direction, swell.directionText)))
  }

}