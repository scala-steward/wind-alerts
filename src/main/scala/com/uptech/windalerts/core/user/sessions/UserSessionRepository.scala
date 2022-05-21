package com.uptech.windalerts.core.user.sessions

import cats.data.OptionT
import cats.mtl.Raise
import com.uptech.windalerts.core.RefreshTokenNotFoundError
import UserSessions.UserSession
import com.uptech.windalerts.core.user.sessions.UserSessions.UserSession

trait UserSessionRepository[F[_]] {
  def create(refreshToken: String, expiry: Long, userId: String, deviceToken: String): F[UserSession]

  def getByUserId(userId: String): OptionT[F, UserSession]

  def getByRefreshToken(refreshToken: String)(implicit RTNF: Raise[F, RefreshTokenNotFoundError]): F[UserSession]

  def deleteForUserId(uid: String): F[Unit]

  def updateDeviceToken(userId: String, deviceToken: String): F[Unit]
}