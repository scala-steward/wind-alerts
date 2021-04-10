package com.uptech.windalerts.core.social.subscriptions

import cats.data.EitherT
import com.uptech.windalerts.domain.{SurfsUpError, TokenNotFoundError}

trait AppleTokenRepository[F[_]] {
  def getPurchaseByToken(purchaseToken: String): EitherT[F, TokenNotFoundError, AppleToken]

  def getLastForUser(userId: String): EitherT[F, TokenNotFoundError, AppleToken]

  def create(token: AppleToken): EitherT[F, SurfsUpError, AppleToken]
}