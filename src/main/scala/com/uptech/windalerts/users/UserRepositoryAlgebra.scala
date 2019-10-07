package com.uptech.windalerts.users


import cats.data.OptionT
import cats.effect.IO
import com.uptech.windalerts.domain.domain.{Credentials, User}

trait UserRepositoryAlgebra {
  def getById(id: String): IO[Option[User]]

  def create(user: User): IO[User]

  def update(user: User): OptionT[IO, User]

  def get(userId: String): OptionT[IO, User]

  def delete(userId: String): OptionT[IO, User]

  def deleteByUserName(userName: String): OptionT[IO, User]
}