package com.uptech.windalerts.core.beaches

import cats.Functor
import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.BeachId

trait SwellsService[F[_]] {
  def get(beachId: BeachId)(implicit F: Functor[F]): cats.data.EitherT[F, SurfsUpError, domain.Swell]
}
