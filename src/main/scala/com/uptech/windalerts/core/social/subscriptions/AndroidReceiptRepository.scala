package com.uptech.windalerts.core.social.subscriptions

import cats.data.EitherT
import cats.effect.IO
import com.uptech.windalerts.domain.{SurfsUpError, TokenNotFoundError}

trait AndroidTokenRepository[F[_]]  {
  def getPurchaseByToken(purchaseToken: String) :EitherT[F, TokenNotFoundError, AndroidToken]

  def getLastForUser(userId: String):EitherT[F, TokenNotFoundError, AndroidToken]

  def create(token: AndroidToken):EitherT[F, TokenNotFoundError, AndroidToken]
}