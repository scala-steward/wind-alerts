package com.uptech.windalerts.core.credentials

import cats.Applicative
import cats.data.{EitherT, OptionT}
import cats.effect.Sync
import cats.implicits._
import com.github.t3hnar.bcrypt._
import com.uptech.windalerts.core._
import com.uptech.windalerts.core.refresh.tokens.UserSessionRepository
import com.uptech.windalerts.core.user.UserRepository
import com.uptech.windalerts.infrastructure.EmailSender
import com.uptech.windalerts.infrastructure.endpoints.dtos.{ChangePasswordRequest, RegisterRequest}

class UserCredentialService[F[_] : Sync](
                                          facebookCredentialsRepo: SocialCredentialsRepository[F, FacebookCredentials],
                                          appleCredentialsRepo: SocialCredentialsRepository[F, AppleCredentials],
                                          credentialsRepository: CredentialsRepository[F],
                                          userRepository: UserRepository[F],
                                          refreshTokenRepository: UserSessionRepository[F],
                                          emailSender: EmailSender[F]) {
  def getByCredentials(
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
                   )(implicit A:Applicative[F]): EitherT[F, SurfsUpError, Credentials] =
    for {
      creds <- credentialsRepository.findByCredentials(email, deviceType).toRight(UserAuthenticationFailedError(email))
      newPassword <- EitherT.pure(utils.generateRandomString(10))(A)
      _ <- EitherT.right(credentialsRepository.updatePassword(creds._id.toHexString, newPassword.bcrypt))
      _ <- EitherT.right(refreshTokenRepository.deleteForUserId(creds._id.toHexString))
      user <- userRepository.getByUserId(creds._id.toHexString).toRight(UserNotFoundError("User not found"))
      _ <- EitherT.pure(emailSender.sendResetPassword(user.firstName(), email, newPassword))(A)
    } yield creds


  def changePassword(request: ChangePasswordRequest): EitherT[F, UserAuthenticationFailedError, Unit] = {
    for {
      credentials <- getByCredentials(request.email, request.oldPassword, request.deviceType)
      result <- EitherT.right(credentialsRepository.updatePassword(credentials._id.toHexString, request.newPassword.bcrypt))
    } yield result
  }

  def createIfDoesNotExist(rr: RegisterRequest): EitherT[F, UserAlreadyExistsError, Credentials] = {
    val credentials = Credentials(rr.email, rr.password.bcrypt, rr.deviceType)
    for {
      _ <- doesNotExist(credentials.email, credentials.deviceType)
      savedCredentials <- EitherT.right(credentialsRepository.create(credentials))
    } yield savedCredentials
  }

  def doesNotExist(email: String, deviceType: String): EitherT[F, UserAlreadyExistsError, Unit] = {
    EitherT((for {
      doesNotExistAsEmailUser <- credentialsRepository.findByCredentials(email, deviceType).isEmpty
      doesNotExistAsFacebookUser <- OptionT(facebookCredentialsRepo.find(email, deviceType)).isEmpty
      doesNotExistAsAppleUser <- OptionT(appleCredentialsRepo.find(email, deviceType)).isEmpty
    } yield (doesNotExistAsEmailUser && doesNotExistAsFacebookUser && doesNotExistAsAppleUser))
      .map(doesNotExist => Either.cond(doesNotExist, (), UserAlreadyExistsError(email, deviceType))))
  }

}
