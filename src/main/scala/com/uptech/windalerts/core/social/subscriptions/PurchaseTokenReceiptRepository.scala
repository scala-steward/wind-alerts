package com.uptech.windalerts.core.social.subscriptions

import cats.data.EitherT
import com.uptech.windalerts.core.{SurfsUpError, TokenNotFoundError}

trait PurchaseTokenRepository[F[_]] {
  def getPurchaseByToken(purchaseToken: String): EitherT[F, TokenNotFoundError, PurchaseToken]

  def getLastForUser(userId: String): EitherT[F, TokenNotFoundError, PurchaseToken]

  def create(token: PurchaseToken): EitherT[F, SurfsUpError, PurchaseToken]
}