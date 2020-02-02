package com.uptech.windalerts.status

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.domain.domain.{Beach, BeachId, SwellOutput, Tide}


class BeachService[F[_] : Sync](W: WindsService[F],
                                T: TidesService[F],
                                S: SwellsService[F]) {

  def get(beachId: BeachId): EitherT[F, Exception, Beach] = {
    for {
      wind <- W.get(beachId)
      tide <- T.get(beachId)
      swell <- S.get(beachId)
    } yield Beach(beachId, wind, Tide(tide, SwellOutput(swell.height, swell.direction, swell.directionText)))
  }

  def getAll(beachIds: Seq[BeachId]): EitherT[F, Exception, Map[BeachId, Beach]] = {
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
