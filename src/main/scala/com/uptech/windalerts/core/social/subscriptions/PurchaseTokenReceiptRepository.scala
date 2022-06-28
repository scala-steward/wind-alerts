package com.uptech.windalerts.core.social.subscriptions

import cats.mtl.Raise
import com.uptech.windalerts.core.TokenNotFoundError

trait PurchaseTokenRepository[F[_]] {
  def getByToken(purchaseToken: String)(implicit FR: Raise[F, TokenNotFoundError]): F[PurchaseToken]

  def getLastForUser(userId: String)(implicit FR: Raise[F, TokenNotFoundError]): F[PurchaseToken]

  def create(userId: String, purchaseToken: String, creationTime: Long): F[PurchaseToken]
}