package com.uptech.windalerts.core.beaches

import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.core.beaches.SwellsService.GetSwellsStatus
import com.uptech.windalerts.core.beaches.domain._


trait SwellsService[F[_]] {
  def get: GetSwellsStatus[F]
}

object SwellsService {
  type GetSwellsStatus[F[_]] = BeachId => cats.data.EitherT[F, SurfsUpError, Swell]
}