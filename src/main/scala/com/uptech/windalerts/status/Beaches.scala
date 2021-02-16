package com.uptech.windalerts.status

import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.domain.WWError
import com.uptech.windalerts.domain.domain._


class BeachService[F[_] : Sync](W: WindsService[F],
                                T: TidesService[F],
                                S: SwellsService[F]) {

  def get(beachId: BeachId): SurfsUpEitherT[F, Beach] = {
    val beach = for {
      wind <- W.get(beachId)
      tide <- T.get(beachId)
      swell <- S.get(beachId)
    } yield Beach(beachId, wind, Tide(tide, SwellOutput(swell.height, swell.direction, swell.directionText)))
    beach.leftMap(_=>WWError())
  }

  def getAll(beachIds: Seq[BeachId]): SurfsUpEitherT[F, Map[BeachId, Beach]] = {
    beachIds
      .toList
      .map(get(_))
      .sequence
      .map(_.seq)
      .map(elem => elem
        .map(s => (s.beachId, s))
        .toMap)
  }
}
