package com.uptech.windalerts.core.credentials

import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import cats.{Applicative, Monad}
import com.github.t3hnar.bcrypt._
import com.uptech.windalerts.core._
import com.uptech.windalerts.core.refresh.tokens.UserSessionRepository
import com.uptech.windalerts.core.social.SocialPlatformType
import com.uptech.windalerts.core.types.{ChangePasswordRequest, RegisterRequest}
import com.uptech.windalerts.core.user.UserRepository

class UserCredentialService[F[_] : Sync](
                                          socialCredentialsRepositories: Map[SocialPlatformType, SocialCredentialsRepository[F]],
                                          credentialsRepository: CredentialsRepository[F],
                                          userRepository: UserRepository[F],
                                          userSessionsRepository: UserSessionRepository[F],
                                          emailSender: EmailSender[F]) {
  def findByCredentials(
                         email: String, password: String, deviceType: String
                       )(implicit FR: Raise[F, UserAuthenticationFailedError]): F[Credentials] =
    for {
      credentials <- credentialsRepository.findByEmailAndDeviceType(email, deviceType).getOrElseF(FR.raise(UserAuthenticationFailedError(email)))
      passwordMatched <- isPasswordMatch(password, credentials)
    } yield passwordMatched

  private def isPasswordMatch(password: String, creds: Credentials)(implicit FR: Raise[F, UserAuthenticationFailedError]) = {
    if(password.isBcrypted(creds.password)) Applicative[F].pure(creds) else FR.raise(UserAuthenticationFailedError(creds.email))
  }

  def resetPassword(
                     email: String, deviceType: String
                   )(implicit F: Monad[F], UAF: Raise[F, UserAuthenticationFailedError], UNF: Raise[F, UserNotFoundError]): F[Credentials] =
    for {
      credentials <- credentialsRepository.findByEmailAndDeviceType(email, deviceType).getOrElseF(UAF.raise(UserAuthenticationFailedError(email)))
      newPassword = utils.generateRandomString(10)
      _ <- credentialsRepository.updatePassword(credentials.id, newPassword.bcrypt)
      _ <- userSessionsRepository.deleteForUserId(credentials.id)
      user <- userRepository.getByUserId(credentials.id)
      _ <- emailSender.sendResetPassword(user.firstName(), email, newPassword)
    } yield credentials


  def changePassword(request: ChangePasswordRequest)(implicit FR: Raise[F, UserAlreadyExistsError], UAF: Raise[F, UserAuthenticationFailedError]): F[Unit] = {
    for {
      credentials <- findByCredentials(request.email, request.oldPassword, request.deviceType)
      result <- credentialsRepository.updatePassword(credentials.id, request.newPassword.bcrypt)
    } yield result
  }

  def register(rr: RegisterRequest)(implicit FR: Raise[F, UserAlreadyExistsError]): F[Credentials] = {
    for {
      _ <- notRegistered(rr.email, rr.deviceType)
      savedCredentials <- credentialsRepository.create(rr.email, rr.password.bcrypt, rr.deviceType)
    } yield savedCredentials
  }

  def notRegistered(email: String, deviceType: String)(implicit FR: Raise[F, UserAlreadyExistsError]): F[Unit] = {
    for {
      notRegisteredAsEmailUser <- credentialsRepository.findByEmailAndDeviceType(email, deviceType).isEmpty
      notRegisteredAsSocialUser <- notRegisteredAsSocialUser(email, deviceType)
      doesNotExist = notRegisteredAsEmailUser && notRegisteredAsSocialUser

    } yield (if (doesNotExist) Applicative[F].pure(()) else FR.raise(UserAlreadyExistsError(email, deviceType)))
  }

  private def notRegisteredAsSocialUser(email: String, deviceType: String) = {
    socialCredentialsRepositories
      .values
      .map(_.find(email, deviceType).map(_.isDefined))
      .toList
      .sequence
      .map(!_.exists(_ == true))
  }
}
