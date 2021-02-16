package com.uptech.windalerts.core.beaches

import cats.Functor
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.{BeachId, SurfsUpEitherT}

trait SwellService[F[_]] {
  def get(beachId: BeachId)(implicit F: Functor[F]): SurfsUpEitherT[F, domain.Swell]
}
