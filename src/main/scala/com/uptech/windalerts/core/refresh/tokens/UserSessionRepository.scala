package com.uptech.windalerts.core.refresh.tokens

import cats.data.{EitherT, OptionT}
import com.uptech.windalerts.core.TokenNotFoundError

trait UserSessionRepository[F[_]] {
  def create(refreshToken: UserSession): F[UserSession]

  def getByRefreshToken(refreshToken: String): OptionT[F, UserSession]

  def deleteForUserId(uid: String): F[Unit]

  def updateExpiry(id: String, expiry: Long): EitherT[F, TokenNotFoundError, UserSession]
}