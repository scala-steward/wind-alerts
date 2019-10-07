package com.uptech.windalerts.users

import cats.data.OptionT
import cats.effect.IO
import com.uptech.windalerts.domain.domain.RefreshToken

trait RefreshTokenRepositoryAlgebra {
  def create(refreshToken: RefreshToken): IO[RefreshToken]

  def getByRefreshToken(refreshToken: String): OptionT[IO, RefreshToken]
}
