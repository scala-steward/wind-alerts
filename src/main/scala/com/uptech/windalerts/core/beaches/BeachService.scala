package com.uptech.windalerts.core.beaches

import cats.{Monad, Parallel}
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.core.beaches.domain._
import com.uptech.windalerts.logger


class BeachService[F[_]](windService: WindsService[F],
                         tidesService: TidesService[F],
                         swellsService: SwellsService[F]) {

  def getStatus(beachId: BeachId)(implicit M: Monad[F]
                                  , P: Parallel[F]
  ): cats.data.EitherT[F, SurfsUpError, Beach] = {
    (windService.get(beachId), tidesService.get(beachId), swellsService.get(beachId))
      .parMapN((wind, tide, swell) =>
        Beach(beachId, wind, Tide(tide, SwellOutput(swell.height, swell.direction, swell.directionText))))
  }

  def getAll(beachIds: Seq[BeachId])(implicit M: Monad[F], P: Parallel[F]): cats.data.EitherT[F, SurfsUpError, Map[BeachId, Beach]] =
    beachIds.traverse(getStatus(_)).map(beaches => beaches.map(beach => (beach.beachId, beach)).toMap)
}