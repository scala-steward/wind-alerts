package com.uptech.windalerts.core.user

import cats.Applicative
import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import com.uptech.windalerts.core._
import com.uptech.windalerts.core.credentials.{Credentials, UserCredentialService}
import com.uptech.windalerts.core.refresh.tokens.UserSession.REFRESH_TOKEN_EXPIRY
import com.uptech.windalerts.core.refresh.tokens.{UserSession, UserSessionRepository}
import com.uptech.windalerts.core.types._


class UserService[F[_] : Sync](userRepository: UserRepository[F],
                               userCredentialsService: UserCredentialService[F],
                               auth: AuthenticationService[F],
                               userSessionsRepository: UserSessionRepository[F],
                               eventPublisher: EventPublisher[F]) {
  def register(registerRequest: RegisterRequest)(implicit FR: Raise[F, UserAlreadyExistsError]): F[TokensWithUser] = {
    for {
      createUserResponse <- persistUserAndCredentials(registerRequest)
      tokens <- generateNewTokens(createUserResponse._1, registerRequest.deviceToken)
      _ <- eventPublisher.publishUserRegistered("userRegistered", UserRegistered(UserIdDTO(createUserResponse._1.id), EmailId(createUserResponse._1.email)))
    } yield tokens
  }

  def persistUserAndCredentials(rr: RegisterRequest)(implicit FR: Raise[F, UserAlreadyExistsError]): F[(UserT, Credentials)] = {
    for {
      savedCreds <- userCredentialsService.register(rr)
      saved <- userRepository.create(UserT.createEmailUser(savedCreds.id, rr.email, rr.name, rr.deviceType))
    } yield (saved, savedCreds)
  }

  def login(credentials: LoginRequest)(implicit FR: Raise[F, UserNotFoundError], UAF: Raise[F, UserAuthenticationFailedError]):F[TokensWithUser] = {
    for {
      persistedCredentials <- userCredentialsService.findByCredentials(credentials.email, credentials.password, credentials.deviceType)
      tokens <- resetUserSession(persistedCredentials.email, persistedCredentials.deviceType, credentials.deviceToken)
    } yield tokens
  }

  def resetUserSession(emailId: String, deviceType: String, newDeviceToken: String)(implicit FR: Raise[F, UserNotFoundError]): F[TokensWithUser] = {
    for {
      persistedUser <- getUser(emailId, deviceType)
      tokens <- resetUserSession(persistedUser, newDeviceToken)
    } yield tokens
  }

  def resetUserSession(dbUser: UserT, newDeviceToken: String):F[TokensWithUser] = {
    for {
      _ <- userSessionsRepository.deleteForUserId(dbUser.id)
      tokens <- generateNewTokens(dbUser, newDeviceToken)
    } yield tokens
  }

  def generateNewTokens(user: UserT, deviceToken: String): F[TokensWithUser] = {
    import cats.syntax.functor._

    val token = auth.createToken(UserId(user.id))

    userSessionsRepository.create(utils.generateRandomString(40), System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY, user.id, deviceToken)
      .map(newRefreshToken => TokensWithUser(token.accessToken, newRefreshToken.refreshToken, token.expiredAt, user))

  }

  def refresh(accessTokenRequest: AccessTokenRequest)(implicit FR: Raise[F, UserNotFoundError], RTNF: Raise[F, RefreshTokenNotFoundError],  RTE: Raise[F, RefreshTokenExpiredError]): F[TokensWithUser] = {
    for {
      oldRefreshToken <- userSessionsRepository.getByRefreshToken(accessTokenRequest.refreshToken)
      _ <- checkNotExpired(oldRefreshToken)
      _ <- userSessionsRepository.deleteForUserId(oldRefreshToken.userId)
      user <- getUser(oldRefreshToken.userId)
      tokens <- generateNewTokens(user, oldRefreshToken.deviceToken)
    } yield tokens
  }

  private def checkNotExpired(oldRefreshToken: UserSession)(implicit RTNF: Raise[F, RefreshTokenExpiredError], A: Applicative[F]) =
    if (!oldRefreshToken.isExpired()) {
      A.pure(())
    } else {
      RTNF.raise(RefreshTokenExpiredError())
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

  def updateDeviceToken(id: String, deviceToken: String)(implicit FR: Raise[F, UserNotFoundError]): F[UserT] = {
    for {
      _ <- userSessionsRepository.updateDeviceToken(id, deviceToken)
      user <- getUser(id)
    } yield user
  }

  def logoutUser(userId: String): EitherT[F, UserNotFoundError, Unit] =
    EitherT.liftF(userSessionsRepository.deleteForUserId(userId))

  def getUser(email: String, deviceType: String)(implicit FR: Raise[F, UserNotFoundError]): F[UserT] =
    userRepository.getByEmailAndDeviceType(email, deviceType)

  def getUser(userId: String)(implicit FR: Raise[F, UserNotFoundError]): F[UserT] =
    userRepository.getByUserId(userId)

}

object UserService {
  def apply[F[_] : Sync](userRepository: UserRepository[F], userCredentialService: UserCredentialService[F], authenticationService: AuthenticationService[F], refreshTokenRepo: UserSessionRepository[F], eventPublisher: EventPublisher[F]): UserService[F] =
    new UserService[F](userRepository, userCredentialService, authenticationService, refreshTokenRepo, eventPublisher)
}