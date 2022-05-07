package com.uptech.windalerts.core.beaches

import cats.data.EitherT
import cats.implicits._
import cats.{Monad, Parallel}
import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.core.beaches.SwellsService.GetSwellsStatus
import com.uptech.windalerts.core.beaches.TidesService.GetTidesStatus
import com.uptech.windalerts.core.beaches.WindsService.GetWindStatus
import com.uptech.windalerts.core.beaches.domain._

object BeachService {
  def getStatus[F[_]]
  (beachId: BeachId)(implicit getWindStatus: GetWindStatus[F],
                     getTideStatus: GetTidesStatus[F],
                     getSwellsStatus: GetSwellsStatus[F],
                     M: Monad[F],
                     P: Parallel[F]
  ): EitherT[F, SurfsUpError, Beach] = {
    (getWindStatus(beachId), getTideStatus(beachId), getSwellsStatus(beachId))
      .parMapN(
        (wind, tide, swell) =>
          Beach(beachId, wind, Tide(tide, SwellOutput(swell.height, swell.direction, swell.directionText))))
  }

  def getAll[F[_]](beachIds: Seq[BeachId])(implicit getWindStatus: GetWindStatus[F],
                                           getTideStatus: GetTidesStatus[F],
                                           getSwellsStatus: GetSwellsStatus[F],
                                           M: Monad[F], P: Parallel[F]): EitherT[F, SurfsUpError, Map[BeachId, Beach]] =
    beachIds.traverse(getStatus(_)).map(beaches => beaches.map(beach => (beach.beachId, beach)).toMap)

}