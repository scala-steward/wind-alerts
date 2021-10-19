package com.uptech.windalerts.core.user

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.core._
import com.uptech.windalerts.core.credentials.{Credentials, UserCredentialService}
import com.uptech.windalerts.core.refresh.tokens.{UserSession, UserSessionRepository}
import com.uptech.windalerts.infrastructure.endpoints.codecs._
import com.uptech.windalerts.infrastructure.endpoints.dtos._
import io.circe.syntax._
import org.mongodb.scala.bson.ObjectId


class UserService[F[_] : Sync](userRepository: UserRepository[F],
                               userCredentialsService: UserCredentialService[F],
                               auth: AuthenticationService[F],
                               userSessionsRepository: UserSessionRepository[F],
                               eventPublisher: EventPublisher[F]) {
  def register(registerRequest: RegisterRequest): EitherT[F, UserAlreadyExistsError, TokensWithUser] = {
    for {
      createUserResponse <- persistUserAndCredentials(registerRequest)
      tokens <- EitherT.right(generateNewTokens(createUserResponse._1, registerRequest.deviceToken))
      _ <- EitherT.right(eventPublisher.publish("userRegistered", UserRegistered(UserIdDTO(createUserResponse._1._id.toHexString), EmailId(createUserResponse._1.email)).asJson))
    } yield tokens
  }

  def persistUserAndCredentials(rr: RegisterRequest): EitherT[F, UserAlreadyExistsError, (UserT, Credentials)] = {
    for {
      savedCreds <- userCredentialsService.createIfDoesNotExist(rr)
      saved <- EitherT.right(userRepository.create(UserT.createEmailUser(new ObjectId(savedCreds._id.toHexString), rr.email, rr.name, rr.deviceType)))
    } yield (saved, savedCreds)
  }

  def login(credentials: LoginRequest): EitherT[F, SurfsUpError, TokensWithUser] = {
    for {
      persistedCredentials <- userCredentialsService.findByCredentials(credentials.email, credentials.password, credentials.deviceType)
      tokens <- resetUserSession(persistedCredentials.email, persistedCredentials.deviceType, credentials.deviceToken).leftWiden[SurfsUpError]
    } yield tokens
  }

  def resetUserSession(emailId: String, deviceType:String, newDeviceToken: String):EitherT[F, UserNotFoundError, TokensWithUser] = {
    for {
      persistedUser <- getUser(emailId, deviceType)
      _ <- EitherT.liftF(userSessionsRepository.deleteForUserId(persistedUser._id.toHexString))
      tokens <- EitherT.right(generateNewTokens(persistedUser, newDeviceToken))
    } yield tokens
  }

  def resetUserSession(dbUser: UserT, newDeviceToken: String):EitherT[F, UserNotFoundError, TokensWithUser] = {
    for {
      _ <- EitherT.liftF(userSessionsRepository.deleteForUserId(dbUser._id.toHexString))
      tokens <- EitherT.right(generateNewTokens(dbUser, newDeviceToken))
    } yield tokens
  }

  def generateNewTokens(user:UserT, deviceToken:String): F[TokensWithUser] = {
    import cats.syntax.functor._

    val token = auth.createToken(UserId(user._id.toHexString), EmailId(user.email), user.firstName(), UserType(user.userType))

    userSessionsRepository.create(UserSession(user._id.toHexString, deviceToken))
      .map(newRefreshToken=>TokensWithUser(token.accessToken, newRefreshToken.refreshToken, token.expiredAt, user))

  }

  def refresh(accessTokenRequest: AccessTokenRequest): EitherT[F, SurfsUpError, TokensWithUser] = {
    for {
      oldRefreshToken <- userSessionsRepository.getByRefreshToken(accessTokenRequest.refreshToken).toRight(RefreshTokenNotFoundError())
      _ <- checkNotExpired(oldRefreshToken)
      _ <- EitherT.liftF(userSessionsRepository.deleteForUserId(oldRefreshToken.userId))
      user <- getUser(oldRefreshToken.userId)
      tokens <- EitherT.right[SurfsUpError](generateNewTokens(user, oldRefreshToken.deviceToken))
    } yield tokens
  }

  private def checkNotExpired(oldRefreshToken: UserSession): cats.data.EitherT[F, SurfsUpError, Unit] =
    EitherT.cond(!oldRefreshToken.isExpired(), (), RefreshTokenExpiredError())

  def updateUserProfile(id: String, name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long): EitherT[F, UserNotFoundError, UserT] = {
    for {
      user <- getUser(id)
      operationResult <- updateUser(name, snoozeTill, disableAllAlerts, notificationsPerHour, user).toRight(UserNotFoundError())
    } yield operationResult
  }

  private def updateUser(name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, user: UserT) = {
    userRepository.update(user.copy(name = name, snoozeTill = snoozeTill, disableAllAlerts = disableAllAlerts, notificationsPerHour = notificationsPerHour))
  }

  def updateDeviceToken(id: String, deviceToken: String): EitherT[F, UserNotFoundError, UserT] = {
    for {
      _ <- EitherT.liftF(userSessionsRepository.updateDeviceToken(id, deviceToken))
      user <- getUser(id)
    } yield user
  }


  def logoutUser(userId: String): EitherT[F, UserNotFoundError, Unit] = {
    for {
      _ <- EitherT.liftF(userSessionsRepository.deleteForUserId(userId))
    } yield ()
  }

  def getUser(email: String, deviceType: String): EitherT[F, UserNotFoundError, UserT] =
    userRepository.getByEmailAndDeviceType(email, deviceType).toRight(UserNotFoundError())

  def getUser(userId: String): EitherT[F, UserNotFoundError, UserT] =
    userRepository.getByUserId(userId).toRight(UserNotFoundError())

}

object UserService {

  def apply[F[_] : Sync](userRepository: UserRepository[F], userCredentialService: UserCredentialService[F], authenticationService: AuthenticationService[F], refreshTokenRepo: UserSessionRepository[F], eventPublisher: EventPublisher[F]): UserService[F] =
    new UserService[F](userRepository, userCredentialService, authenticationService, refreshTokenRepo, eventPublisher)
}