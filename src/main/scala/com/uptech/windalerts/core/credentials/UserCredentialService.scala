package com.uptech.windalerts.core.credentials

import cats.data.EitherT
import cats.effect.Sync
import com.github.t3hnar.bcrypt._
import com.uptech.windalerts.core.refresh.tokens.RefreshTokenRepository
import com.uptech.windalerts.core.user.UserRepository
import com.uptech.windalerts.core._
import com.uptech.windalerts.infrastructure.EmailSender
import com.uptech.windalerts.infrastructure.endpoints.dtos.{ChangePasswordRequest, RegisterRequest}

class UserCredentialService[F[_] : Sync](
                                          facebookCredentialsRepo: SocialCredentialsRepository[F, FacebookCredentials],
                                          appleCredentialsRepo: SocialCredentialsRepository[F, AppleCredentials],
                                          credentialsRepository: CredentialsRepository[F],
                                          userRepository: UserRepository[F],
                                          refreshTokenRepository: RefreshTokenRepository[F],
                                          emailSender: EmailSender[F]) {
  def getByCredentials(
                        email: String, password: String, deviceType: String
                      ): EitherT[F, UserAuthenticationFailedError, Credentials] =
    for {
      creds <- credentialsRepository.findByCredentials(email, deviceType).toRight(UserAuthenticationFailedError(email))
      passwordMatched <- isPasswordMatch(password, creds)
    } yield passwordMatched

  private def isPasswordMatch(password: String, creds: Credentials) = {
    EitherT.cond(password.isBcrypted(creds.password), creds, UserAuthenticationFailedError(creds.email))
  }

  def resetPassword(
                     email: String, deviceType: String
                   ): EitherT[F, SurfsUpError, Credentials] =
    for {
      creds <- credentialsRepository.findByCredentials(email, deviceType).toRight(UserAuthenticationFailedError(email))
      newPassword <- EitherT.pure(utils.generateRandomString(10))
      _ <- EitherT.right(credentialsRepository.updatePassword(creds._id.toHexString, newPassword.bcrypt))
      _ <- EitherT.right(refreshTokenRepository.deleteForUserId(creds._id.toHexString))
      user <- userRepository.getByUserId(creds._id.toHexString).toRight(UserNotFoundError("User not found"))
      _ <- EitherT.liftF(emailSender.sendResetPassword(user.firstName(), email, newPassword))
    } yield creds


  def changePassword(request:ChangePasswordRequest): EitherT[F, UserAuthenticationFailedError, Unit] = {
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

  def doesNotExist(email: String, deviceType: String): EitherT[F, UserAlreadyExistsError, (Unit, Unit, Unit)] = {
    for {
      emailDoesNotExist <- countToEither(credentialsRepository.count(email, deviceType))
      facebookDoesNotExist <- countToEither(facebookCredentialsRepo.count(email, deviceType))
      appleDoesNotExist <- countToEither(appleCredentialsRepo.count(email, deviceType))
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
