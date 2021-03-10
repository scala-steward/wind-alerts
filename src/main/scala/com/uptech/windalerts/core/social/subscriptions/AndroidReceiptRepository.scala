package com.uptech.windalerts.core.social.subscriptions

import cats.data.EitherT
import com.uptech.windalerts.domain.SurfsUpError

trait AndroidTokenRepository[F[_]]  {
  def getPurchaseByToken(purchaseToken: String) : EitherT[F, SurfsUpError, AndroidToken]

  def getLastForUser(userId: String): EitherT[F, SurfsUpError, AndroidToken]

  def create(token: AndroidToken): EitherT[F, SurfsUpError, AndroidToken]
}