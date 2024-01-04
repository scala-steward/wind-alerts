package com.uptech.windalerts.core.user.credentials

import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import cats.{Applicative, Monad}
import com.github.t3hnar.bcrypt._
import com.uptech.windalerts.core._
import com.uptech.windalerts.core.social.SocialPlatformType
import com.uptech.windalerts.core.types.{ChangePasswordRequest, RegisterRequest}


object UserCredentialService {
  def findByEmailAndPassword[F[_]:Sync](
                                    email: String, password: String, deviceType: String
                                  )(implicit infrastructure: Infrastructure[F], FR: Raise[F, UserAuthenticationFailedError]): F[Credentials] =
    for {
      credentials <- infrastructure.credentialsRepository.findByEmailAndDeviceType(email, deviceType).getOrElseF(FR.raise(UserAuthenticationFailedError(email)))
      passwordMatched <- isPasswordMatch(password, credentials)
    } yield passwordMatched

  private def isPasswordMatch[F[_]](password: String, creds: Credentials)(implicit infrastructure: Infrastructure[F], A:Applicative[F], FR: Raise[F, UserAuthenticationFailedError]) = {
    if (password.isBcrypted(creds.password)) A.pure(creds) else FR.raise(UserAuthenticationFailedError(creds.email))
  }

  def resetPassword[F[_]](
                           email: String, deviceType: String
                         )(implicit infrastructure: Infrastructure[F], F: Monad[F], UAF: Raise[F, UserAuthenticationFailedError]): F[Credentials] =
    for {
      credentials <- infrastructure.credentialsRepository.findByEmailAndDeviceType(email, deviceType).getOrElseF(UAF.raise(UserAuthenticationFailedError(email)))
      newPassword = utils.generateRandomString(10)
      _ <- infrastructure.credentialsRepository.updatePassword(credentials.id, newPassword.bcrypt)
    } yield credentials.copy(password = newPassword)

  def changePassword[F[_]:Sync](request: ChangePasswordRequest)(implicit infrastructure: Infrastructure[F], FR: Raise[F, UserAlreadyExistsRegistered], UAF: Raise[F, UserAuthenticationFailedError]): F[Unit] = {
    for {
      credentials <- findByEmailAndPassword(request.email, request.oldPassword, request.deviceType)
      result <- infrastructure.credentialsRepository.updatePassword(credentials.id, request.newPassword.bcrypt)
    } yield result
  }

  def register[F[_]](rr: RegisterRequest)(implicit infrastructure: Infrastructure[F], M:Monad[F], FR: Raise[F, UserAlreadyExistsRegistered]): F[Credentials] = {
    for {
      _ <- notRegistered(rr.email, rr.deviceType)
      savedCredentials <- infrastructure.credentialsRepository.create(rr.email, rr.password.bcrypt, rr.deviceType)
    } yield savedCredentials
  }

  def notRegistered[F[_]](email: String, deviceType: String)(implicit infrastructure: Infrastructure[F], M:Monad[F], FR: Raise[F, UserAlreadyExistsRegistered]): F[Unit] = {
    for {
      notRegisteredAsEmailUser <- infrastructure.credentialsRepository.findByEmailAndDeviceType(email, deviceType).isEmpty
      notRegisteredAsSocialUser <- notRegisteredAsSocialUser(email, deviceType)
      notRegistered <- if (notRegisteredAsEmailUser && notRegisteredAsSocialUser) {
        M.pure(())
      } else {
        FR.raise(UserAlreadyExistsRegistered(email, deviceType))
      }
    } yield notRegistered
  }

  private def notRegisteredAsSocialUser[F[_]](email: String, deviceType: String)(implicit infrastructure: Infrastructure[F], M:Monad[F]) = {
    infrastructure.socialCredentialsRepositories
      .values
      .map(_.find(email, deviceType).map(_.isDefined))
      .toList
      .sequence
      .map(!_.exists(_ == true))
  }

  def deleteByEmailId[F[_]](emailId: String)(implicit infrastructure: Infrastructure[F], M: Monad[F]) = {
    infrastructure.credentialsRepository.deleteByEmailId(emailId)
  }
}
