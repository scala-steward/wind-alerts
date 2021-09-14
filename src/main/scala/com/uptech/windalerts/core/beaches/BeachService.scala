package com.uptech.windalerts.core.beaches

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.core.beaches.domain._


class BeachService[F[_]](windService: WindsService[F],
                         tidesService: TidesService[F],
                         swellsService: SwellsService[F]) {

  def getStatus(beachId: BeachId)(implicit M: Monad[F]): cats.data.EitherT[F, SurfsUpError, Beach] = {
    for {
      wind <- windService.get(beachId)
      tide <- tidesService.get(beachId)
      swell <- swellsService.get(beachId)
    } yield Beach(beachId, wind, Tide(tide, SwellOutput(swell.height, swell.direction, swell.directionText)))
  }

  def getAll(beachIds: Seq[BeachId])(implicit M: Monad[F]): cats.data.EitherT[F, SurfsUpError, Map[BeachId, Beach]] = {
    beachIds
      .toList
      .map(getStatus(_))
      .sequence
      .map(_.seq)
      .map(elem => elem
        .map(s => (s.beachId, s))
        .toMap)
  }
}