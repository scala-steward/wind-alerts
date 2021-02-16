package com.uptech.windalerts.social.subcriptions

import cats.data.EitherT
import com.uptech.windalerts.domain.SurfsUpError
import com.uptech.windalerts.domain.domain.AppleToken

trait AppleTokenRepository[F[_]] {
  def getPurchaseByToken(purchaseToken: String): EitherT[F, SurfsUpError, AppleToken]

  def getLastForUser(userId: String): EitherT[F, SurfsUpError, AppleToken]

  def create(token: AppleToken): EitherT[F, SurfsUpError, AppleToken]
}