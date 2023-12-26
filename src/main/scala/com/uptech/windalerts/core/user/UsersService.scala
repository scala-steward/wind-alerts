package com.uptech.windalerts.core.user

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import com.uptech.windalerts.core._
import com.uptech.windalerts.core.user.credentials.{Credentials, UserCredentialService}
import com.uptech.windalerts.core.types._
import com.uptech.windalerts.core.user.sessions.UserSessions

object UserService {
  def register[F[_]:Sync](registerRequest: RegisterRequest)(implicit infrastructure: Infrastructure[F], FR: Raise[F, UserAlreadyExistsRegistered], M: Monad[F]): F[TokensWithUser] = {
    for {
      createUserResponse <- register_(registerRequest)
      tokens <- UserSessions.generateNewTokens(createUserResponse._1.id, registerRequest.deviceToken)
      tokensWithUser = TokensWithUser(tokens, createUserResponse._1)
      _ <- infrastructure.eventPublisher.publishUserRegistered("userRegistered", UserRegistered(UserIdDTO(createUserResponse._1.id), EmailId(createUserResponse._1.email)))
    } yield tokensWithUser
  }

  def login[F[_]:Sync](loginRequest: LoginRequest)(implicit  infrastructure: Infrastructure[F], FR: Raise[F, UserNotFoundError], UAF: Raise[F, UserAuthenticationFailedError]): F[TokensWithUser] = {
    for {
      persistedCredentials <- UserCredentialService.findByEmailAndPassword(loginRequest.email, loginRequest.password, loginRequest.deviceType)
      tokens <- UserSessions.reset(persistedCredentials.id, loginRequest.deviceToken)
      tokensWithUser <- getTokensWithUser(persistedCredentials.id, tokens)
    } yield tokensWithUser
  }

  def refresh[F[_]:Sync](accessTokenRequest: AccessTokenRequest)(implicit  infrastructure: Infrastructure[F], FR: Raise[F, UserNotFoundError], RTNF: Raise[F, RefreshTokenNotFoundError], RTE: Raise[F, RefreshTokenExpiredError]): F[TokensWithUser] = {
    for {
      tokens <- UserSessions.refresh(accessTokenRequest)
      tokensWithUser <- getTokensWithUser(tokens.refreshToken.userId, tokens)
    } yield tokensWithUser
  }

  def getTokensWithUser[F[_]](id: String, tokens: Tokens)(implicit  infrastructure: Infrastructure[F], M:Monad[F], FR: Raise[F, UserNotFoundError]): F[TokensWithUser] = {
    for {
      user <- getUser(id)
      tokensWithUser = TokensWithUser(tokens, user)
    } yield tokensWithUser
  }

  def resetPassword[F[_]](
                     email: String, deviceType: String
                   )(implicit infrastructure: Infrastructure[F], F: Monad[F], UAF: Raise[F, UserAuthenticationFailedError], UNF: Raise[F, UserNotFoundError]): F[Unit] =
    for {
      credentials <- UserCredentialService.resetPassword(email, deviceType)
      _ <- UserSessions.deleteForUserId(credentials.id)
      user <- infrastructure.userRepository.getByUserId(credentials.id)
      _ <- infrastructure.passwordNotifier.notifyNewPassword(user.firstName(), email, credentials.password)
    } yield ()

  def logout[F[_]](userId: String)(implicit infrastructure: Infrastructure[F]): F[Unit] =
    UserSessions.deleteForUserId(userId)

  def register_[F[_]](rr: RegisterRequest)(implicit  infrastructure: Infrastructure[F], M:Monad[F], FR: Raise[F, UserAlreadyExistsRegistered]): F[(UserT, Credentials)] = {
    for {
      savedCreds <- UserCredentialService.register(rr)
      saved <- infrastructure.userRepository.create(UserT.createEmailUser(savedCreds.id, rr.email, rr.name, rr.deviceType))
    } yield (saved, savedCreds)
  }

  def updateUserProfile[F[_]](id: String, name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long)(implicit  infrastructure: Infrastructure[F], M:Monad[F], FR: Raise[F, UserNotFoundError]): F[UserT] = {
    for {
      user <- getUser(id)
      operationResult <- updateUser(name, snoozeTill, disableAllAlerts, notificationsPerHour, user)
    } yield operationResult
  }

  private def updateUser[F[_]](name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, user: UserT)(implicit  infrastructure: Infrastructure[F], FR: Raise[F, UserNotFoundError]) =
    infrastructure.userRepository.update(user.copy(name = name, snoozeTill = snoozeTill, disableAllAlerts = disableAllAlerts, notificationsPerHour = notificationsPerHour))

  def getUser[F[_]](email: String, deviceType: String)(implicit infrastructure: Infrastructure[F], FR: Raise[F, UserNotFoundError]): F[UserT] =
    infrastructure.userRepository.getByEmailAndDeviceType(email, deviceType)

  def getUser[F[_]](userId: String)(implicit  infrastructure: Infrastructure[F],  FR: Raise[F, UserNotFoundError]): F[UserT] =
    infrastructure.userRepository.getByUserId(userId)

  def deleteUserByUserId[F[_]](userId: String)(implicit infrastructure: Infrastructure[F], FR: Raise[F, UserNotFoundError]) =
    infrastructure.userRepository.deleteUserById(userId)

}