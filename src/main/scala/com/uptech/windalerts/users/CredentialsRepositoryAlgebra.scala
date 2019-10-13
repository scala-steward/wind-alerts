package com.uptech.windalerts.users

import cats.data.{EitherT, OptionT}
import cats.effect.IO
import com.uptech.windalerts.domain.domain.{Credentials, FacebookCredentials}

trait CredentialsRepositoryAlgebra {
  def doesNotExist(email:String, deviceType:String): EitherT[IO, UserAlreadyExistsError, Unit]

  def exists(userId: String): EitherT[IO, UserNotFoundError.type, Unit]

  def create(credentials: Credentials): IO[Credentials]

  def create(credentials: FacebookCredentials): IO[FacebookCredentials]

  def update(user: Credentials): OptionT[IO, Credentials]

  def get(userId: String): OptionT[IO, Credentials]

  def delete(userId: String): OptionT[IO, Credentials]

  def findByCreds(email: String, password:String, deviceType: String): OptionT[IO, Credentials]

  def updatePassword(userId: String, password: String) :OptionT[IO, Unit]

}
