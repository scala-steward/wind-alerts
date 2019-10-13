package com.uptech.windalerts.users

import cats.{Functor, Monad}
import cats.data._
import cats.effect.IO
import cats.syntax.functor._
import com.restfb.{DefaultFacebookClient, Parameter, Version}
import com.uptech.windalerts.domain.domain.UserType.{Premium, Registered, Trial}
import com.uptech.windalerts.domain.domain.{Credentials, FacebookCredentials, FacebookRegisterRequest, RegisterRequest, User, UserType}
import com.uptech.windalerts.users.FbClient.facebookClient

class UserService(userRepo: UserRepositoryAlgebra, credentialsRepo: CredentialsRepositoryAlgebra) {
  def updateUserProfile(id: String, name: String, userType: UserType, snoozeTill: Long): EitherT[IO, ValidationError, User] = {
    for {
      user <- getUser(id)
      operationResult <- updateTypeAllowed(userType, name, snoozeTill, user)
    } yield operationResult
  }

  private def updateTypeAllowed(newUserType: UserType, name: String, snoozeTill: Long, user: User): EitherT[IO, ValidationError, User] = {
    UserType(user.userType) match {
      case Registered | Trial => {
        newUserType match {
          case Trial => {
            val newStartTrial = if (user.startTrialAt == -1) System.currentTimeMillis() else user.startTrialAt
            userRepo.update(user.copy(userType = newUserType.value, name = name, startTrialAt = newStartTrial, snoozeTill = snoozeTill)).toRight(CouldNotUpdateUserTypeError())
          }
          case anyOtherType => EitherT.left(IO(OperationNotAllowed(s"${anyOtherType.value} user can not be updated to ${newUserType.value}")))
        }
      }
      case anyOtherType => EitherT.left(IO(OperationNotAllowed(s"${anyOtherType.value} user can not be updated to ${newUserType.value}")))
    }
  }

  def updateDeviceToken(userId: String, deviceToken: String) =
    userRepo.updateDeviceToken(userId, deviceToken)

  def updatePassword(userId: String, password: String): OptionT[IO, Unit] =
    credentialsRepo.updatePassword(userId, password)

  def createUser(rr: FacebookRegisterRequest): EitherT[IO, UserAlreadyExistsError, (User, FacebookCredentials)] = {
    val facebookClient = new DefaultFacebookClient(rr.token, "06cee1aa51e14d50c40f28b47a6b7501", Version.LATEST);

    for {
      fbuser <- EitherT.liftF(IO(facebookClient.fetchObject("me", classOf[com.restfb.types.User], Parameter.`with`("fields", "name,id,email"))))

      _ <- credentialsRepo.doesNotExist(fbuser.getEmail, rr.deviceToken)
      savedCreds <- EitherT.liftF(credentialsRepo.create(FacebookCredentials(None, fbuser.getEmail, rr.token, rr.deviceType)))
      saved <- EitherT.liftF(userRepo.create(User(savedCreds.id.get, fbuser.getEmail, fbuser.getName, rr.deviceId, rr.deviceToken, rr.deviceType, System.currentTimeMillis(), -1, Registered.value, -1)))
    } yield (saved, savedCreds)
  }

  def createUser(rr: RegisterRequest): EitherT[IO, UserAlreadyExistsError, User] = {
    val credentials = Credentials(None, rr.email, rr.password, rr.deviceType)
    for {
      _ <- credentialsRepo.doesNotExist(credentials.email, credentials.deviceType)
      savedCreds <- EitherT.liftF(credentialsRepo.create(credentials))
      saved <- EitherT.liftF(userRepo.create(User(savedCreds.id.get, rr.email, rr.name, rr.deviceId, rr.deviceToken, rr.deviceType, System.currentTimeMillis(), -1, Registered.value, -1)))
    } yield saved
  }

  def getUser(email: String, deviceType: String): EitherT[IO, UserNotFoundError, User] =
    OptionT(userRepo.getByEmailAndDeviceType(email, deviceType)).toRight(UserNotFoundError())

  def getUser(userId: String): EitherT[IO, UserNotFoundError, User] =
    OptionT(userRepo.getByUserId(userId)).toRight(UserNotFoundError())

  def getByCredentials(
                        email: String, password: String, deviceType: String
                      ): EitherT[IO, ValidationError, Credentials] =
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