package com.uptech.windalerts.core.user

import cats.{FlatMap, Monad}
import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import com.uptech.windalerts.core._
import com.uptech.windalerts.core.credentials.{Credentials, UserCredentialService}
import com.uptech.windalerts.core.refresh.tokens.UserSessions
import com.uptech.windalerts.core.types._


class UserService[F[_] : Sync](userRepository: UserRepository[F],
                               userCredentialsService: UserCredentialService[F],
                               userSessions:UserSessions[F],
                               eventPublisher: EventPublisher[F]) {
  def register(registerRequest: RegisterRequest)(implicit FR: Raise[F, UserAlreadyExistsRegistered], M:Monad[F]): F[TokensWithUser] = {
    for {
      createUserResponse <- persistUserAndCredentials(registerRequest)
      tokens <- userSessions.generateNewTokens(createUserResponse._1, registerRequest.deviceToken)
      _ <- eventPublisher.publishUserRegistered("userRegistered", UserRegistered(UserIdDTO(createUserResponse._1.id), EmailId(createUserResponse._1.email)))
    } yield tokens
  }

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