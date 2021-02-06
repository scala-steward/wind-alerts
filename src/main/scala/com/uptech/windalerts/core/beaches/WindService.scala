package com.uptech.windalerts.core.beaches

import cats.Functor
import com.uptech.windalerts.domain.domain
import com.uptech.windalerts.domain.domain.SurfsUpEitherT

trait WindService[F[_]] {
  def get(beachId: domain.BeachId)(implicit F: Functor[F]): SurfsUpEitherT[F, domain.Wind]
}
