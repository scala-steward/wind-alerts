package com.uptech.windalerts.core.credentials

import cats.data.EitherT
import cats.effect.Sync
import com.github.t3hnar.bcrypt._
import com.uptech.windalerts.Repos
import com.uptech.windalerts.core.utils
import com.uptech.windalerts.domain.domain.{ChangePasswordRequest, SurfsUpEitherT}
import com.uptech.windalerts.domain.{CouldNotUpdatePasswordError, SurfsUpError, UserAuthenticationFailedError, UserNotFoundError}
import cats.syntax.functor._

class UserCredentialService[F[_] : Sync](repos: Repos[F])  {
  def getByCredentials(
                        email: String, password: String, deviceType: String
                      ): EitherT[F, UserAuthenticationFailedError, Credentials] =
    for {
      creds <- repos.credentialsRepo().findByCreds(email, deviceType).toRight(UserAuthenticationFailedError(email))
      passwordMatched <- isPasswordMatch(password, creds)
    } yield passwordMatched

  private def isPasswordMatch(password: String, creds: Credentials) = {
    EitherT.fromEither(if (password.isBcrypted(creds.password)) {
      Right(creds)
    } else {
      Left(UserAuthenticationFailedError(creds.email))
    })
  }

  def resetPassword(
                     email: String, deviceType: String
                   ): EitherT[F, SurfsUpError, Credentials] =
    for {
      creds <- repos.credentialsRepo().findByCreds(email, deviceType).toRight(UserAuthenticationFailedError(email))
      newPassword <- EitherT.pure(utils.generateRandomString(10))
      _ <- EitherT.right(updatePassword(creds._id.toHexString, newPassword))
      _ <- EitherT.right(repos.refreshTokenRepo().deleteForUserId(creds._id.toHexString))
      user <- repos.usersRepo().getByUserId(creds._id.toHexString).toRight(UserNotFoundError())
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

}
