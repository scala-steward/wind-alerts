package com.uptech.windalerts.status


import cats.effect.IO
import com.uptech.windalerts.domain.Domain
import com.uptech.windalerts.domain.Domain.{Beach, BeachId, Tide}


trait Beaches extends Serializable {
  val beaches: Beaches.Service
}

object Beaches {

  trait Service extends Serializable {
    def get(beachId: BeachId): IO[Domain.Beach]
  }

  final case class ServiceImpl[R](W: Winds.Service, S: Swells.Service, T: Tides.Service) extends Service {
    override def get(beachId: BeachId): IO[Beach] = {
      for {
        wind <- W.get(beachId)
        tide <- T.get(beachId)
        swell <- S.get(beachId)
      } yield Beach(wind, Tide(tide, swell))
    }
  }

}