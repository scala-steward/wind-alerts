package com.uptech.windalerts.core.credentials

import cats.Applicative
import cats.data.{EitherT, OptionT}
import cats.effect.Sync
import cats.implicits._
import com.github.t3hnar.bcrypt._
import com.uptech.windalerts.core._
import com.uptech.windalerts.core.refresh.tokens.UserSessionRepository
import com.uptech.windalerts.core.social.SocialPlatformType
import com.uptech.windalerts.core.user.UserRepository
import com.uptech.windalerts.infrastructure.EmailSender
import com.uptech.windalerts.infrastructure.endpoints.dtos.{ChangePasswordRequest, RegisterRequest}

class UserCredentialService[F[_] : Sync](
                                          socialCredentialsRepositories:Map[SocialPlatformType, SocialCredentialsRepository[F]],
                                          credentialsRepository: CredentialsRepository[F],
                                          userRepository: UserRepository[F],
                                          userSessionsRepository: UserSessionRepository[F],
                                          emailSender: EmailSender[F]) {
  def findByCredentials(
                         email: String, password: String, deviceType: String
                       ): EitherT[F, UserAuthenticationFailedError, Credentials] =
    for {
      creds <- credentialsRepository.findByCredentials(email, deviceType).toRight(UserAuthenticationFailedError(email))
      passwordMatched <- isPasswordMatch(password, creds)
    } yield passwordMatched

  private def isPasswordMatch(password: String, creds: Credentials) = {
    EitherT.cond[F](password.isBcrypted(creds.password), creds, UserAuthenticationFailedError(creds.email))
  }

  def resetPassword(
                     email: String, deviceType: String
                   )(implicit A: Applicative[F]): EitherT[F, SurfsUpError, Credentials] =
    for {
      creds <- credentialsRepository.findByCredentials(email, deviceType).toRight(UserAuthenticationFailedError(email))
      newPassword = utils.generateRandomString(10)
      _ <- EitherT.right(credentialsRepository.updatePassword(creds.id, newPassword.bcrypt))
      _ <- EitherT.right(userSessionsRepository.deleteForUserId(creds.id))
      user <- userRepository.getByUserId(creds.id).toRight(UserNotFoundError("User not found"))
      _ <- emailSender.sendResetPassword(user.firstName(), email, newPassword).leftMap[SurfsUpError](UnknownError(_))
    } yield creds


  def changePassword(request: ChangePasswordRequest): EitherT[F, UserAuthenticationFailedError, Unit] = {
    for {
      credentials <- findByCredentials(request.email, request.oldPassword, request.deviceType)
      result <- EitherT.right(credentialsRepository.updatePassword(credentials.id, request.newPassword.bcrypt))
    } yield result
  }

  def createIfDoesNotExist(rr: RegisterRequest): EitherT[F, UserAlreadyExistsError, Credentials] = {
    for {
      _ <- doesNotExist(rr.email, rr.deviceType)
      savedCredentials <- EitherT.right(credentialsRepository.create(rr.email, rr.password, rr.deviceType))
    } yield savedCredentials
  }

  def doesNotExist(email: String, deviceType: String): EitherT[F, UserAlreadyExistsError, Unit] = {
    EitherT((for {
      doesNotExistAsEmailUser <- credentialsRepository.findByCredentials(email, deviceType).isEmpty
      doesNotExistAsSocialUser <- doesNotExistAsSocialUser(email, deviceType)
    } yield (doesNotExistAsEmailUser && doesNotExistAsSocialUser))
      .map(doesNotExist => Either.cond(doesNotExist, (), UserAlreadyExistsError(email, deviceType))))
  }

  private def doesNotExistAsSocialUser(email: String, deviceType: String) = {
    socialCredentialsRepositories
      .values
      .map(_.find(email, deviceType).map(_.isDefined))
      .toList
      .sequence
      .map(!_.exists(_ == true))
  }
}
