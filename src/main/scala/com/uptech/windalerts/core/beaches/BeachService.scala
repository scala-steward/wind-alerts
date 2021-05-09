package com.uptech.windalerts.core.beaches

import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.domain.domain._


class BeachService[F[_] : Sync](windService: WindsService[F],
                                tidesService: TidesService[F],
                                swellsService: SwellsService[F]) {

  def get(beachId: BeachId): cats.data.EitherT[F, SurfsUpError, Beach] = {
    for {
      wind <- windService.get(beachId)
      tide <- tidesService.get(beachId)
      swell <- swellsService.get(beachId)
    } yield Beach(beachId, wind, Tide(tide, SwellOutput(swell.height, swell.direction, swell.directionText)))
  }

  def getAll(beachIds: Seq[BeachId]): cats.data.EitherT[F, SurfsUpError, Map[BeachId, Beach]] = {
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
