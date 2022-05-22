package com.uptech.windalerts.core.user

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import com.uptech.windalerts.core._
import com.uptech.windalerts.core.credentials.{Credentials, UserCredentialService}
import com.uptech.windalerts.core.types._
import com.uptech.windalerts.core.user.sessions.UserSessions


class UserService[F[_] : Sync](userRepository: UserRepository[F],
                               userCredentialsService: UserCredentialService[F],
                               userSessions: UserSessions[F],
                               eventPublisher: EventPublisher[F],
                               passwordNotifier: PasswordNotifier[F]) {
  def register(registerRequest: RegisterRequest)(implicit FR: Raise[F, UserAlreadyExistsRegistered], M: Monad[F]): F[TokensWithUser] = {
    for {
      createUserResponse <- persistUserAndCredentials(registerRequest)
      tokens <- userSessions.generateNewTokens(createUserResponse._1.id, registerRequest.deviceToken)
      tokensWithUser = TokensWithUser(tokens.accessToken, tokens.refreshToken.refreshToken, tokens.expiredAt, createUserResponse._1)
      _ <- eventPublisher.publishUserRegistered("userRegistered", UserRegistered(UserIdDTO(createUserResponse._1.id), EmailId(createUserResponse._1.email)))
    } yield tokensWithUser
  }

  def login(credentials: LoginRequest)(implicit FR: Raise[F, UserNotFoundError], UAF: Raise[F, UserAuthenticationFailedError]): F[TokensWithUser] = {
    for {
      persistedCredentials <- userCredentialsService.findByEmailAndPassword(credentials.email, credentials.password, credentials.deviceType)
      tokens <- userSessions.reset(persistedCredentials.id, credentials.deviceToken)
      tokensWithUser <- getTokensWithUser(persistedCredentials.id, tokens)
    } yield tokensWithUser
  }

  def refresh(accessTokenRequest: AccessTokenRequest)(implicit FR: Raise[F, UserNotFoundError], RTNF: Raise[F, RefreshTokenNotFoundError], RTE: Raise[F, RefreshTokenExpiredError]): F[TokensWithUser] = {
    for {
      tokens <- userSessions.refresh(accessTokenRequest)
      tokensWithUser <- getTokensWithUser(tokens.refreshToken.userId, tokens)
    } yield tokensWithUser
  }

  def getTokensWithUser(id: String, tokens: Tokens)(implicit FR: Raise[F, UserNotFoundError]): F[TokensWithUser] = {
    for {
      user <- getUser(id)
      tokensWithUser = TokensWithUser(tokens.accessToken, tokens.refreshToken.refreshToken, tokens.expiredAt, user)
    } yield tokensWithUser
  }

  def resetPassword(
                     email: String, deviceType: String
                   )(implicit F: Monad[F], UAF: Raise[F, UserAuthenticationFailedError], UNF: Raise[F, UserNotFoundError]): F[Unit] =
    for {
      credentials <- userCredentialsService.resetPassword(email, deviceType)
      _ <- userSessions.deleteForUserId(credentials.id)
      user <- userRepository.getByUserId(credentials.id)
      _ <- passwordNotifier.notifyNewPassword(user.firstName(), email, credentials.password)
    } yield ()


  def logout(userId: String): F[Unit] =
    userSessions.deleteForUserId(userId)

  def persistUserAndCredentials(rr: RegisterRequest)(implicit FR: Raise[F, UserAlreadyExistsRegistered]): F[(UserT, Credentials)] = {
    for {
      savedCreds <- userCredentialsService.register(rr)
      saved <- userRepository.create(UserT.createEmailUser(savedCreds.id, rr.email, rr.name, rr.deviceType))
    } yield (saved, savedCreds)
  }

  def updateUserProfile(id: String, name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long)(implicit FR: Raise[F, UserNotFoundError]): F[UserT] = {
    for {
      user <- getUser(id)
      operationResult <- updateUser(name, snoozeTill, disableAllAlerts, notificationsPerHour, user)
    } yield operationResult
  }

  private def updateUser(name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, user: UserT)(implicit FR: Raise[F, UserNotFoundError]) = {
    userRepository.update(user.copy(name = name, snoozeTill = snoozeTill, disableAllAlerts = disableAllAlerts, notificationsPerHour = notificationsPerHour))
  }

  def getUser(email: String, deviceType: String)(implicit FR: Raise[F, UserNotFoundError]): F[UserT] =
    userRepository.getByEmailAndDeviceType(email, deviceType)

  def getUser(userId: String)(implicit FR: Raise[F, UserNotFoundError]): F[UserT] =
    userRepository.getByUserId(userId)
}