package com.uptech.windalerts.core.user

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.github.t3hnar.bcrypt._
import com.uptech.windalerts.Repos
import com.uptech.windalerts.core.credentials.{Credentials, UserCredentialService}
import com.uptech.windalerts.core.feedbacks.Feedback
import com.uptech.windalerts.core.otp.OTPService
import com.uptech.windalerts.core.refresh.tokens.RefreshToken
import com.uptech.windalerts.core.utils
import com.uptech.windalerts.domain._
import com.uptech.windalerts.domain.domain.{SurfsUpEitherT, _}
import org.mongodb.scala.bson.ObjectId

class UserService[F[_] : Sync](repos: Repos[F], userCredentialsService: UserCredentialService[F], otpService: OTPService[F], auth: AuthenticationService[F]) {

  def register(registerRequest: RegisterRequest): EitherT[F, UserAlreadyExistsError, TokensWithUser] = {
    for {
      createUserResponse <- create(registerRequest)
      _ <- EitherT.right(otpService.send(createUserResponse._1._id.toHexString, createUserResponse._1.email))
      tokens <- EitherT.right(generateNewTokens(createUserResponse._1))
    } yield tokens
  }

  def create(rr: RegisterRequest): EitherT[F, UserAlreadyExistsError, (UserT, Credentials)] = {
    val credentials = Credentials(rr.email, rr.password.bcrypt, rr.deviceType)
    for {
      _ <- doesNotExist(credentials.email, credentials.deviceType)
      savedCreds <- EitherT.right(repos.credentialsRepo().create(credentials))
      saved <- EitherT.right(repos.usersRepo().create(
        UserT.createEmailUser(new ObjectId(savedCreds._id.toHexString),
          rr.email,
          rr.name,
          rr.deviceToken,
          rr.deviceType)))
    } yield (saved, savedCreds)
  }

  def login(credentials: LoginRequest): EitherT[F, SurfsUpError, TokensWithUser] = {
    for {
      dbCredentials <- userCredentialsService.getByCredentials(credentials.email, credentials.password, credentials.deviceType)
      dbUser <- getUser(dbCredentials.email, dbCredentials.deviceType)
      tokens <- resetUserSession(dbUser, credentials.deviceToken).leftWiden[SurfsUpError]
    } yield tokens
  }


  def resetUserSession(dbUser: UserT, newDeviceToken: String):EitherT[F, UserNotFoundError, TokensWithUser] = {
    for {
      _ <- updateDeviceToken(dbUser._id.toHexString, newDeviceToken)
      _ <- EitherT.liftF(repos.refreshTokenRepo().deleteForUserId(dbUser._id.toHexString))
      tokens <- EitherT.right(generateNewTokens(dbUser.copy(deviceToken = newDeviceToken)))
    } yield tokens
  }

  def refresh(refreshToken: AccessTokenRequest): EitherT[F, SurfsUpError, TokensWithUser] = {
    for {
      oldRefreshToken <- repos.refreshTokenRepo().getByRefreshToken(refreshToken.refreshToken).toRight(RefreshTokenNotFoundError())
      oldValidRefreshToken <- {
        val eitherT: SurfsUpEitherT[F, RefreshToken] = {
          if (oldRefreshToken.isExpired()) {
            EitherT.fromEither(Left(RefreshTokenExpiredError()))
          } else {
            import cats.implicits._
            repos.refreshTokenRepo().updateExpiry(oldRefreshToken._id.toHexString, (System.currentTimeMillis() + RefreshToken.REFRESH_TOKEN_EXPIRY)).leftWiden[SurfsUpError]
          }
        }
        eitherT
      }
      _ <- EitherT.liftF(repos.refreshTokenRepo().deleteForUserId(oldValidRefreshToken.userId))
      user <- getUser(oldRefreshToken.userId)
      tokens <- EitherT.right[SurfsUpError](generateNewTokens(user))
    } yield tokens
  }

  def generateNewTokens(user: UserT): F[TokensWithUser] = {
    import cats.syntax.functor._

    val accessTokenId = utils.generateRandomString(10)
    val token = auth.createToken(user._id.toHexString, accessTokenId)

    repos.refreshTokenRepo().create(RefreshToken(user._id.toHexString, accessTokenId))
      .map(newRefreshToken=>auth.tokens(token.accessToken, newRefreshToken, token.expiredAt, user))
  }

  def updateUserProfile(id: String, name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long): EitherT[F, UserNotFoundError, UserT] = {
    for {
      user <- getUser(id)
      operationResult <- updateUser(name, snoozeTill, disableAllAlerts, notificationsPerHour, user).toRight(UserNotFoundError())
    } yield operationResult
  }

  private def updateUser(name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, user: UserT) = {
    repos.usersRepo().update(user.copy(name = name, snoozeTill = snoozeTill, disableAllAlerts = disableAllAlerts, notificationsPerHour = notificationsPerHour))
  }

  def updateDeviceToken(id: String, deviceToken: String): EitherT[F, UserNotFoundError, UserT] = {
    for {
      user <- getUser(id)
      operationResult <- repos.usersRepo().update(user.copy(deviceToken = deviceToken)).toRight(UserNotFoundError())
    } yield operationResult
  }


  def logoutUser(userId: String): EitherT[F, UserNotFoundError, Unit] = {
    for {
      _ <- EitherT.liftF(repos.refreshTokenRepo().deleteForUserId(userId))
      _ <- updateDeviceToken(userId, "")
    } yield ()
  }

  def doesNotExist(email: String, deviceType: String): EitherT[F, UserAlreadyExistsError, (Unit, Unit, Unit)] = {
    for {
      emailDoesNotExist <- countToEither(repos.credentialsRepo().count(email, deviceType))
      facebookDoesNotExist <- countToEither(repos.facebookCredentialsRepo().count(email, deviceType))
      appleDoesNotExist <- countToEither(repos.appleCredentialsRepository().count(email, deviceType))
    } yield (emailDoesNotExist, facebookDoesNotExist, appleDoesNotExist)
  }

  private def countToEither(count: F[Int]) : EitherT[F, UserAlreadyExistsError, Unit] = {
    EitherT.liftF(count).flatMap(c => {
      val e: Either[UserAlreadyExistsError, Unit] = if (c > 0) Left(UserAlreadyExistsError("", ""))
      else Right(())
      EitherT.fromEither(e)
    })
  }

  def getUser(email: String, deviceType: String): EitherT[F, UserNotFoundError, UserT] =
    repos.usersRepo().getByEmailAndDeviceType(email, deviceType).toRight(UserNotFoundError())

  def getUser(userId: String): EitherT[F, UserNotFoundError, UserT] =
    repos.usersRepo().getByUserId(userId).toRight(UserNotFoundError())

  def createFeedback(feedback: Feedback): F[Feedback] = {
    repos.feedbackRepository.create(feedback)
  }

  def sendOtp(userId: String): EitherT[F, UserNotFoundError, Unit] = {
    for {
      userFromDb <- getUser(userId)
      sent <- EitherT.right(otpService.send(userFromDb._id.toHexString, userFromDb.email))
    } yield sent
  }

}

object UserService {
  def apply[F[_] : Sync](
                          repos: Repos[F],
                          userCredentialService: UserCredentialService[F],
                          otpService: OTPService[F],
                          authenticationService: AuthenticationService[F]
                        ): UserService[F] =
    new UserService(repos, userCredentialService, otpService, authenticationService)
}