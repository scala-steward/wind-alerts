package com.uptech.windalerts.users

import cats.data.EitherT
import cats.effect.Sync
import com.github.t3hnar.bcrypt._
import com.uptech.windalerts.Repos
import com.uptech.windalerts.domain.{UserAuthenticationFailedError, conversions, _}
import domain.{ChangePasswordRequest, Credentials, SurfsUpEitherT}

class UserCredentialService[F[_] : Sync](repos: Repos[F])  {
  def getByCredentials(
                        email: String, password: String, deviceType: String
                      ): SurfsUpEitherT[F, Credentials] =
    for {
      creds <- repos.credentialsRepo().findByCreds(email, deviceType).toRight(UserAuthenticationFailedError(email))
      passwordMatched <- isPasswordMatch(password, creds)
    } yield passwordMatched

  private def isPasswordMatch(password: String, creds: Credentials): SurfsUpEitherT[F, Credentials] = {
    EitherT.fromEither(if (password.isBcrypted(creds.password)) {
      Right(creds)
    } else {
      Left(UserAuthenticationFailedError(creds.email))
    })
  }

  def resetPassword(
                     email: String, deviceType: String
                   ): SurfsUpEitherT[F, Credentials] =
    for {
      creds <- repos.credentialsRepo().findByCreds(email, deviceType).toRight(UserAuthenticationFailedError(email))
      newPassword <- EitherT.pure(conversions.generateRandomString(10))
      _ <- updatePassword(creds._id.toHexString, newPassword)
      _ <- EitherT.liftF(repos.refreshTokenRepo().deleteForUserId(creds._id.toHexString))
      user <- EitherT.liftF(repos.usersRepo().getByUserId(creds._id.toHexString))
      _ <- EitherT.pure(repos.emailConf().sendResetPassword(user.get.firstName(), email, newPassword))
    } yield creds


  def changePassword(request:ChangePasswordRequest): SurfsUpEitherT[F, Unit] = {
    for {
      credentials <- getByCredentials(request.email, request.oldPassword, request.deviceType)
      result <- updatePassword(credentials._id.toHexString, request.newPassword)
    } yield result
  }

  def updatePassword(userId: String, password: String): SurfsUpEitherT[F, Unit] = {
    repos.credentialsRepo().updatePassword(userId, password.bcrypt).toRight(CouldNotUpdatePasswordError())
  }

}
