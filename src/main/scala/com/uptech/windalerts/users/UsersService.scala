package com.uptech.windalerts.users

import cats.{Functor, Monad}
import cats.data._
import cats.effect.IO
import cats.syntax.functor._
import com.uptech.windalerts.domain.domain.{Credentials, RegisterRequest, User}

class UserService(userRepo: UserRepositoryAlgebra, credentialsRepo: CredentialsRepositoryAlgebra) {
  def updateDeviceToken(userId:String, deviceToken:String)  =
    userRepo.updateDeviceToken(userId, deviceToken)

  def createUser(rr: RegisterRequest): EitherT[IO, UserAlreadyExistsError, User] = {
    val credentials = Credentials(None, rr.email, rr.password, rr.deviceType)
    for {
      _ <- credentialsRepo.doesNotExist(credentials)
      savedCreds <- EitherT.liftF(credentialsRepo.create(credentials))
      saved <- EitherT.liftF(userRepo.create(User(savedCreds.id.get, rr.email, rr.name, rr.deviceId, rr.deviceToken, rr.deviceType)))
    } yield saved
  }

  def getUser(userId: String): EitherT[IO, UserNotFoundError.type, User] =
    userRepo.get(userId).toRight(UserNotFoundError)

  def getByCredentials(
                        email: String, password:String, deviceType: String
                   ): EitherT[IO, UserAuthenticationFailedError, Credentials] =
    credentialsRepo.findByCreds(email, password, deviceType).toRight(UserAuthenticationFailedError(email))

  def deleteUser(userId: String): IO[Unit] =
    userRepo.delete(userId).value.void

  def deleteByUserName(userName: String)(implicit F: Functor[IO]): IO[Unit] =
    userRepo.deleteByUserName(userName).value.void

  def update(user: User)(implicit M: Monad[IO]): EitherT[IO, UserNotFoundError.type, User] =
    for {
      _ <- credentialsRepo.exists(user.id)
      saved <- userRepo.update(user).toRight(UserNotFoundError)
    } yield saved

}

object UserService {
  def apply[IO[_]](
                    usersRepository: UserRepositoryAlgebra,
                    credentialsRepository: CredentialsRepositoryAlgebra
                  ): UserService =
    new UserService(usersRepository, credentialsRepository)
}