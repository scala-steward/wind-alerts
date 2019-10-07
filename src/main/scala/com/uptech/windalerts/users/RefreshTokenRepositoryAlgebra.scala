package com.uptech.windalerts.users

import cats.data.OptionT
import cats.effect.IO
import com.uptech.windalerts.domain.domain.RefreshToken

trait RefreshTokenRepositoryAlgebra {
  def getByAccessTokenId(accessTokenId: String): OptionT[IO, RefreshToken]

  def create(refreshToken: RefreshToken): IO[RefreshToken]

  def getByRefreshToken(refreshToken: String): OptionT[IO, RefreshToken]

  def deleteForUserId(uid: String): IO[Unit]
}
