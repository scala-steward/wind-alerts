package com.uptech.windalerts.users

import cats.data.{EitherT, OptionT}
import cats.effect.Sync
import com.github.t3hnar.bcrypt._
import com.restfb.{DefaultFacebookClient, Parameter, Version}
import com.uptech.windalerts.Repos
import com.uptech.windalerts.domain.{CouldNotUpdatePasswordError, CouldNotUpdateUserError, UserAlreadyExistsError, UserAuthenticationFailedError, UserNotFoundError, ValidationError, conversions}
import com.uptech.windalerts.domain.domain.UserType._
import com.uptech.windalerts.domain.domain.{Credentials, _}
import org.mongodb.scala.bson.ObjectId

class UserService[F[_] : Sync](repos: Repos[F]) {
  def createUser(rr: RegisterRequest): EitherT[F, UserAlreadyExistsError, UserT] = {
    val credentials = Credentials(rr.email, rr.password.bcrypt, rr.deviceType)
    for {
      _ <- doesNotExist(credentials.email, credentials.deviceType)
      savedCreds <- EitherT.liftF(repos.credentialsRepo().create(credentials))
      saved <- EitherT.liftF(repos.usersRepo().create(UserT.create(new ObjectId(savedCreds._id.toHexString), rr.email, rr.name, rr.deviceId, rr.deviceToken, rr.deviceType, -1, Registered.value, -1, false, 4)))
    } yield saved
  }

  def createUser(rr: FacebookRegisterRequest): EitherT[F, UserAlreadyExistsError, (UserT, FacebookCredentialsT)] = {
    for {
      facebookClient <- EitherT.pure(new DefaultFacebookClient(rr.accessToken, repos.fbSecret(), Version.LATEST))
      facebookUser <- EitherT.pure((facebookClient.fetchObject("me", classOf[com.restfb.types.User], Parameter.`with`("fields", "name,id,email"))))
      _ <- doesNotExist(facebookUser.getEmail, rr.deviceType)
      savedCreds <- EitherT.liftF(repos.facebookCredentialsRepo().create(FacebookCredentialsT(facebookUser.getEmail, rr.accessToken, rr.deviceType)))
      savedUser <- EitherT.liftF(repos.usersRepo().create(UserT.create(new ObjectId(savedCreds._id.toHexString), facebookUser.getEmail, facebookUser.getName, rr.deviceId, rr.deviceToken, rr.deviceType, System.currentTimeMillis(), Trial.value, -1, false, 4)))
    } yield (savedUser, savedCreds)
  }

  def createUser(rr: AppleRegisterRequest): EitherT[F, UserAlreadyExistsError, (UserT, AppleCredentials)] = {
    for {
      appleUser <- EitherT.pure(AppleLogin.getUser(rr.authorizationCode, repos.appleLoginConf()))
      _ <- doesNotExist(appleUser.email, rr.deviceType)

      savedCreds <- EitherT.liftF(repos.appleCredentialsRepository().create(AppleCredentials(appleUser.email, rr.deviceType, appleUser.sub)))
      savedUser <- EitherT.liftF(repos.usersRepo().create(UserT.create(new ObjectId(savedCreds._id.toHexString), appleUser.email, rr.name, rr.deviceId, rr.deviceToken, rr.deviceType, System.currentTimeMillis(), Trial.value, -1, false, 4)))
    } yield (savedUser, savedCreds)
  }

  def loginUser(rr: AppleLoginRequest) = {
    for {
      appleUser <- EitherT.pure(AppleLogin.getUser(rr.authorizationCode, repos.appleLoginConf()))
      _ <- repos.appleCredentialsRepository().findByAppleId(appleUser.sub)
      dbUser <- getUser(appleUser.email, rr.deviceType)
    } yield dbUser
  }

  def updateUserProfile(id: String, name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long): EitherT[F, ValidationError, UserT] = {
    for {
      user <- getUser(id)
      operationResult <- updateUser(name, snoozeTill, disableAllAlerts, notificationsPerHour, user)
    } yield operationResult
  }

  private def updateUser(name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, user: UserT): EitherT[F, ValidationError, UserT] = {
    repos.usersRepo().update(user.copy(name = name, snoozeTill = snoozeTill, disableAllAlerts = disableAllAlerts, notificationsPerHour = notificationsPerHour, lastPaymentAt = -1, nextPaymentAt = -1)).toRight(CouldNotUpdateUserError())
  }

  def updateDeviceToken(userId: String, deviceToken: String) =
    repos.usersRepo().updateDeviceToken(userId, deviceToken)

  def updatePassword(userId: String, password: String): OptionT[F, Unit] =
    repos.credentialsRepo().updatePassword(userId, password.bcrypt)

  def getFacebookUserByAccessToken(accessToken: String, deviceType: String) = {
    for {
      facebookClient <- EitherT.pure((new DefaultFacebookClient(accessToken, repos.fbSecret(), Version.LATEST)))
      facebookUser <- EitherT.pure((facebookClient.fetchObject("me", classOf[com.restfb.types.User], Parameter.`with`("fields", "name,id,email"))))
      dbUser <- getUser(facebookUser.getEmail, deviceType)
    } yield dbUser
  }

  def logoutUser(userId: String) = {
    for {
      _ <- EitherT.liftF(repos.refreshTokenRepo().deleteForUserId(userId))
      _ <- updateDeviceToken(userId, "").toRight(CouldNotUpdateUserError())
    } yield ()
  }

  def doesNotExist(email: String, deviceType: String) = {
    for {
      emailDoesNotExist <- countToEither(repos.credentialsRepo().count(email, deviceType))
      facebookDoesNotExist <- countToEither(repos.facebookCredentialsRepo().count(email, deviceType))
      appleDoesNotExist <- countToEither(repos.appleCredentialsRepository().count(email, deviceType))
    } yield (emailDoesNotExist, facebookDoesNotExist, appleDoesNotExist)
  }

  private def countToEither(count: F[Int]) = {
    EitherT.liftF(count).flatMap(c => {
      val e: Either[UserAlreadyExistsError, Unit] = if (c > 0) Left(UserAlreadyExistsError("", ""))
      else Right(())
      EitherT.fromEither(e)
    })
  }

  def getUser(email: String, deviceType: String): EitherT[F, UserNotFoundError, UserT] =
    OptionT(repos.usersRepo().getByEmailAndDeviceType(email, deviceType)).toRight(UserNotFoundError())

  def getUser(userId: String): EitherT[F, ValidationError, UserT] =
    OptionT(repos.usersRepo().getByUserId(userId)).toRight(UserNotFoundError())

  def getByCredentials(
                        email: String, password: String, deviceType: String
                      ): EitherT[F, ValidationError, Credentials] =
    for {
      creds <- repos.credentialsRepo().findByCreds(email, deviceType).toRight(UserAuthenticationFailedError(email))
      passwordMatched <- isPasswordMatch(password, creds)
    } yield passwordMatched

  def resetPassword(
                     email: String, deviceType: String
                   ): EitherT[F, ValidationError, Credentials] =
    for {
      creds <- repos.credentialsRepo().findByCreds(email, deviceType).toRight(UserAuthenticationFailedError(email))
      newPassword <- EitherT.pure(conversions.generateRandomString(10))
      _ <- updatePassword(creds._id.toHexString, newPassword).toRight(CouldNotUpdatePasswordError())
      _ <- EitherT.pure(repos.emailConf().send(email, "Your new password", newPassword))
    } yield creds


  private def isPasswordMatch(password: String, creds: Credentials): EitherT[F, ValidationError, Credentials] = {
    EitherT.fromEither(if (password.isBcrypted(creds.password)) {
      Right(creds)
    } else {
      Left(UserAuthenticationFailedError(creds.email))
    })
  }

  def createFeedback(feedback: Feedback): EitherT[F, ValidationError, Feedback] = {
    EitherT.liftF(repos.feedbackRepository.create(feedback))
  }

}

object UserService {
  def apply[F[_] : Sync](
                          repos: Repos[F],
                        ): UserService[F] =
    new UserService(repos)
}