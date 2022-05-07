package com.uptech.windalerts.core.beaches

import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.core.beaches.TidesService.GetTidesStatus
import com.uptech.windalerts.core.beaches.domain.{BeachId, TideHeight}

trait TidesService[F[_]] {
  def get: GetTidesStatus[F]
}

object TidesService {
  type GetTidesStatus[F[_]] = BeachId => cats.data.EitherT[F, SurfsUpError, TideHeight]
}