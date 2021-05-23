package com.uptech.windalerts.core.beaches

import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.core.beaches.domain._


trait SwellsService[F[_]] {
  def get(beachId: BeachId): cats.data.EitherT[F, SurfsUpError, Swell]
}
