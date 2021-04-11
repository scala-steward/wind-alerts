package com.uptech.windalerts.core.credentials

import cats.data.EitherT
import cats.effect.Sync
import com.github.t3hnar.bcrypt._
import com.uptech.windalerts.Repos
import com.uptech.windalerts.core.user.UserT
import com.uptech.windalerts.core.utils
import com.uptech.windalerts.domain.domain.{ChangePasswordRequest, RegisterRequest}
import com.uptech.windalerts.domain.{SurfsUpError, UserAlreadyExistsError, UserAuthenticationFailedError, UserNotFoundError}
import org.mongodb.scala.bson.ObjectId

class UserCredentialService[F[_] : Sync](repos: Repos[F])  {
  def getByCredentials(
                        email: String, password: String, deviceType: String
                      ): EitherT[F, UserAuthenticationFailedError, Credentials] =
    for {
      creds <- repos.credentialsRepo().findByCreds(email, deviceType).toRight(UserAuthenticationFailedError(email))
      passwordMatched <- isPasswordMatch(password, creds)
    } yield passwordMatched

  private def isPasswordMatch(password: String, creds: Credentials) = {
    EitherT.cond(password.isBcrypted(creds.password), creds, UserAuthenticationFailedError(creds.email))
  }

  def resetPassword(
                     email: String, deviceType: String
                   ): EitherT[F, SurfsUpError, Credentials] =
    for {
      creds <- repos.credentialsRepo().findByCreds(email, deviceType).toRight(UserAuthenticationFailedError(email))
      newPassword <- EitherT.pure(utils.generateRandomString(10))
      _ <- EitherT.right(updatePassword(creds._id.toHexString, newPassword))
      _ <- EitherT.right(repos.refreshTokenRepo().deleteForUserId(creds._id.toHexString))
      user <- repos.usersRepo().getByUserId(creds._id.toHexString).toRight(UserNotFoundError("User not found"))
      _ <- EitherT.pure(repos.emailConf().sendResetPassword(user.firstName(), email, newPassword))
    } yield creds


  def changePassword(request:ChangePasswordRequest): EitherT[F, UserAuthenticationFailedError, Unit] = {
    for {
      credentials <- getByCredentials(request.email, request.oldPassword, request.deviceType)
      result <- EitherT.right(updatePassword(credentials._id.toHexString, request.newPassword))
    } yield result
  }

  def updatePassword(userId: String, password: String): F[Unit] = {
    repos.credentialsRepo().updatePassword(userId, password.bcrypt)
  }


  def createIfDoesNotExist(rr: RegisterRequest): EitherT[F, UserAlreadyExistsError, Credentials] = {
    val credentials = Credentials(rr.email, rr.password.bcrypt, rr.deviceType)
    for {
      _ <- doesNotExist(credentials.email, credentials.deviceType)
      savedCreds <- EitherT.right(repos.credentialsRepo().create(credentials))
    } yield savedCreds
  }

  def doesNotExist(email: String, deviceType: String): EitherT[F, UserAlreadyExistsError, (Unit, Unit, Unit)] = {
    for {
      emailDoesNotExist <- countToEither(repos.credentialsRepo().count(email, deviceType))
      facebookDoesNotExist <- countToEither(repos.facebookCredentialsRepo().count(email, deviceType))
      appleDoesNotExist <- countToEither(repos.appleCredentialsRepository().count(email, deviceType))
    } yield (emailDoesNotExist, facebookDoesNotExist, appleDoesNotExist)
  }

  private def countToEither(count: F[Int]) : EitherT[F, UserAlreadyExistsError, Unit] = {
    EitherT.liftF(count).flatMap(c => {
      val e: Either[UserAlreadyExistsError, Unit] = if (c > 0) Left(UserAlreadyExistsError("", ""))
      else Right(())
      EitherT.fromEither(e)
    })
  }

}
