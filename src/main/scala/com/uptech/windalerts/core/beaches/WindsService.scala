package com.uptech.windalerts.core.beaches

import cats.Functor
import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.BeachId

trait WindsService[F[_]] {
  def get(beachId: BeachId): cats.data.EitherT[F, SurfsUpError, domain.Wind]
}