package com.uptech.windalerts.users


import cats.data.OptionT
import cats.effect.IO
import com.uptech.windalerts.domain.domain.User

trait UserRepositoryAlgebra {

  def getByUserId(userId: String): IO[Option[User]]

  def getByEmailAndDeviceType(email: String, deviceType: String): IO[Option[User]]

  def create(user: User): IO[User]

  def update(user: User): OptionT[IO, User]

  def delete(userId: String): OptionT[IO, User]

  def deleteByUserName(userName: String): OptionT[IO, User]

  def updateDeviceToken(userId: String, deviceToken: String): OptionT[IO, Unit]

}