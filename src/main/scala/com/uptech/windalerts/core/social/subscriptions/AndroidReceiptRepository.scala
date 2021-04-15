package com.uptech.windalerts.core.social.subscriptions

import cats.data.EitherT
import com.uptech.windalerts.core.TokenNotFoundError

trait AndroidTokenRepository[F[_]]  {
  def getPurchaseByToken(purchaseToken: String) :EitherT[F, TokenNotFoundError, AndroidToken]

  def getLastForUser(userId: String):EitherT[F, TokenNotFoundError, AndroidToken]

  def create(token: AndroidToken):EitherT[F, TokenNotFoundError, AndroidToken]
}