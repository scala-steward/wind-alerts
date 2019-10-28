package com.uptech.windalerts.users

import cats.Functor
import cats.data._
import cats.effect.IO
import cats.syntax.functor._
import com.github.t3hnar.bcrypt._
import com.restfb.{DefaultFacebookClient, Parameter, Version}
import com.uptech.windalerts.alerts.AlertsRepository
import com.uptech.windalerts.domain.domain.UserType._
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.secrets

class UserService(userRepo: UserRepositoryAlgebra,
                  credentialsRepo: CredentialsRepositoryAlgebra,
                  facebookCredentialsRepo: FacebookCredentialsRepositoryAlgebra,
                  alertsRepository: AlertsRepository.Repository,
                  facebookSecretKey: String) {
  def verifyEmail(id: String) = {
    for {
      user <- getUser(id)
      operationResult <- updateUserType(user)
    } yield operationResult
  }

  private def updateUserType(user: User): EitherT[IO, ValidationError, User] = {
    userRepo.update(user.copy(userType = Trial.value)).toRight(CouldNotUpdateUserError())
  }


  def updateUserProfile(id: String, name: String, snoozeTill: Long): EitherT[IO, ValidationError, User] = {
    for {
      user <- getUser(id)
      operationResult <- updateUser(name, snoozeTill, user)
    } yield operationResult
  }

  private def updateUser(name: String, snoozeTill: Long, user: User): EitherT[IO, ValidationError, User] = {
    userRepo.update(user.copy(name = name, snoozeTill = snoozeTill)).toRight(CouldNotUpdateUserError())
  }

  def updateDeviceToken(userId: String, deviceToken: String) =
    userRepo.updateDeviceToken(userId, deviceToken)

  def updatePassword(userId: String, password: String): OptionT[IO, Unit] =
    credentialsRepo.updatePassword(userId, password.bcrypt)

  def getFacebookUserByAccessToken(accessToken: String, deviceType: String) = {
    for {
      facebookClient <- EitherT.liftF(IO(new DefaultFacebookClient(accessToken, facebookSecretKey, Version.LATEST)))
      facebookUser <- EitherT.liftF(IO(facebookClient.fetchObject("me", classOf[com.restfb.types.User], Parameter.`with`("fields", "name,id,email"))))
      dbUser <- getUser(facebookUser.getEmail, deviceType)
    } yield dbUser
  }

  def createUser(rr: FacebookRegisterRequest): EitherT[IO, UserAlreadyExistsError, (User, FacebookCredentials)] = {
    for {
      facebookClient <- EitherT.liftF(IO(new DefaultFacebookClient(rr.accessToken, facebookSecretKey, Version.LATEST)))
      facebookUser <- EitherT.liftF(IO(facebookClient.fetchObject("me", classOf[com.restfb.types.User], Parameter.`with`("fields", "name,id,email"))))
      _ <- doesNotExist(facebookUser.getEmail, rr.deviceType)

      savedCreds <- EitherT.liftF(facebookCredentialsRepo.create(FacebookCredentials(None, facebookUser.getEmail, rr.accessToken, rr.deviceType)))
      savedUser <- EitherT.liftF(userRepo.create(User(savedCreds.id.get, facebookUser.getEmail, facebookUser.getName, rr.deviceId, rr.deviceToken, rr.deviceType, System.currentTimeMillis(), Trial.value, -1)))
    } yield (savedUser, savedCreds)
  }

  def createUser(rr: RegisterRequest): EitherT[IO, UserAlreadyExistsError, User] = {
    val credentials = Credentials(None, rr.email, rr.password.bcrypt, rr.deviceType)
    for {
      _ <- doesNotExist(credentials.email, credentials.deviceType)
      savedCreds <- EitherT.liftF(credentialsRepo.create(credentials))
      saved <- EitherT.liftF(userRepo.create(User(savedCreds.id.get, rr.email, rr.name, rr.deviceId, rr.deviceToken, rr.deviceType, System.currentTimeMillis(), Registered.value, -1)))
    } yield saved
  }


  def doesNotExist(email: String, deviceType: String) = {
    for {
      emailDoesNotExist <- credentialsRepo.doesNotExist(email, deviceType)
      facebookDoesNotExist <- facebookCredentialsRepo.doesNotExist(email, deviceType)
    } yield (emailDoesNotExist, facebookDoesNotExist)
  }

  def getUserAndUpdateRole(userId: String): EitherT[IO, UserNotFoundError, User] = {
    for {
      eitherUser <- OptionT(userRepo.getByUserId(userId)).toRight(UserNotFoundError())
      updated <- updateRole(eitherUser)
    } yield updated
  }

  def getUserAndUpdateRole(email: String, deviceType: String): EitherT[IO, UserNotFoundError, User] = {
    for {
      eitherUser <- OptionT(userRepo.getByEmailAndDeviceType(email, deviceType)).toRight(UserNotFoundError())
      updated <- updateRole(eitherUser)
    } yield updated
  }

  private def updateRole(eitherUser: User): EitherT[IO, UserNotFoundError, User] = {
    if (UserType(eitherUser.userType) == Trial && eitherUser.isTrialEnded()) {
      for {
        updated <- update(eitherUser.copy(userType = UserType.TrialExpired.value))
        _ <- EitherT.liftF(alertsRepository.disableAllButOneAlerts(updated.id))
      } yield updated
    } else {
      EitherT.fromEither(toEither(eitherUser))
    }
  }

  private def toEither(user: User): Either[UserNotFoundError, User] = {
    Right(user)
  }

  def getUser(email: String, deviceType: String): EitherT[IO, UserNotFoundError, User] =
    OptionT(userRepo.getByEmailAndDeviceType(email, deviceType)).toRight(UserNotFoundError())

  def getUser(userId: String): EitherT[IO, ValidationError, User] =
    OptionT(userRepo.getByUserId(userId)).toRight(UserNotFoundError())

  def getByCredentials(
                        email: String, password: String, deviceType: String
                      ): EitherT[IO, ValidationError, Credentials] =
    for {
      creds <- credentialsRepo.findByCreds(email, deviceType).toRight(UserAuthenticationFailedError(email))
      passwordMatched <- isPasswordMatch(password, creds)
    } yield passwordMatched


  private def isPasswordMatch(password: String, creds: Credentials):EitherT[IO, ValidationError, Credentials] = {
    val passwordMatch =
      if (password.isBcrypted(creds.password)) {
        Right(creds)
      } else {
        Left(UserAuthenticationFailedError(creds.email))
      }
    EitherT(IO(passwordMatch))
  }

  def deleteUser(userId: String): IO[Unit] =
    userRepo.delete(userId).value.void

  def deleteByUserName(userName: String)(implicit F: Functor[IO]): IO[Unit] =
    userRepo.deleteByUserName(userName).value.void

  def update(user: User): EitherT[IO, UserNotFoundError, User] =
    for {
      saved <- userRepo.update(user).toRight(UserNotFoundError())
    } yield saved

}

object UserService {
  def apply[IO[_]](
                    usersRepository: UserRepositoryAlgebra,
                    credentialsRepository: CredentialsRepositoryAlgebra,
                    facebookCredentialsRepositoryAlgebra: FacebookCredentialsRepositoryAlgebra,
                    alertsRepository: AlertsRepository.Repository
                  ): UserService =
    new UserService(usersRepository, credentialsRepository, facebookCredentialsRepositoryAlgebra, alertsRepository, secrets.read.surfsUp.facebook.key)
}