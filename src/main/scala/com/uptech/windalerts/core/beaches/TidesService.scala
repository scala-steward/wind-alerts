package com.uptech.windalerts.core.beaches

import cats.Functor
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.{BeachId}

trait TidesService[F[_]] {
  def get(beachId: BeachId)(implicit F: Functor[F]): cats.data.EitherT[F, com.uptech.windalerts.domain.SurfsUpError, domain.TideHeight]
}
