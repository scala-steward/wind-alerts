package com.uptech.windalerts.status

import cats.data.EitherT
import cats.effect.{IO, Sync}
import com.uptech.windalerts.domain.domain.{Beach, BeachId, SwellOutput, Tide}


class BeachService[F[_] : Sync](W: WindsService[F],
                                T: TidesService[F],
                                S: SwellsService[F]) {

  def get(beachId: BeachId): EitherT[F, Exception, Beach] = {
    for {
      wind <- W.get(beachId)
      tide <- T.get(beachId)
      swell <- S.get(beachId)
    } yield Beach(wind, Tide(tide, SwellOutput(swell.height, swell.direction, swell.directionText)))
  }
//
//  def get1(beachId: BeachId): IO[Beach] = {
//    val x = for {
//      wind <- W.get(beachId)
//      tide <- T.get(beachId)
//      swell <- S.get(beachId)
//    } yield Beach(wind, Tide(tide, SwellOutput(swell.height, swell.direction, swell.directionText)))
//    IO(x.value)
//  }
}
