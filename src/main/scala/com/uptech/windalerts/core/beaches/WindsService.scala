package com.uptech.windalerts.core.beaches

import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.core.beaches.WindsService.GetWindStatus
import com.uptech.windalerts.core.beaches.domain._

trait WindsService[F[_]] {
  def get:GetWindStatus[F]
}

object WindsService {
  type GetWindStatus[F[_]] = BeachId => cats.data.EitherT[F, SurfsUpError, Wind]
}