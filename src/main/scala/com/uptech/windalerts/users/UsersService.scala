package com.uptech.windalerts.users

import cats.data.{EitherT, OptionT}
import cats.effect.Sync
import com.github.t3hnar.bcrypt._
import com.uptech.windalerts.Repos
import com.uptech.windalerts.core.feedbacks.Feedback
import com.uptech.windalerts.domain._
import domain.{Credentials, SurfsUpEitherT, _}
import org.mongodb.scala.bson.ObjectId

class UserService[F[_] : Sync](repos: Repos[F], userCredentialsService:UserCredentialService[F], otpService: OTPService[F], auth: AuthenticationService[F]) {

  def register(registerRequest: RegisterRequest): SurfsUpEitherT[F, TokensWithUser] = {
    for {
      createUserResponse <- createUser(registerRequest)
      _ <- otpService.send(createUserResponse._1._id.toHexString, createUserResponse._1.email)
      tokens <- generateNewTokens(createUserResponse._1)
    } yield tokens
  }

  def createUser(rr: RegisterRequest): SurfsUpEitherT[F, (UserT, Credentials)] = {
    val credentials = Credentials(rr.email, rr.password.bcrypt, rr.deviceType)
    for {
      _ <- doesNotExist(credentials.email, credentials.deviceType)
      savedCreds <- EitherT.liftF(repos.credentialsRepo().create(credentials))
      saved <- EitherT.liftF(repos.usersRepo().create(
        UserT.createEmailUser(new ObjectId(savedCreds._id.toHexString),
          rr.email,
          rr.name,
          rr.deviceToken,
          rr.deviceType)))
    } yield (saved, savedCreds)
  }

  def login(credentials:LoginRequest): SurfsUpEitherT[F, TokensWithUser] = {
    for {
      dbCredentials <- userCredentialsService.getByCredentials(credentials.email, credentials.password, credentials.deviceType)
      dbUser <- getUser(dbCredentials.email, dbCredentials.deviceType)
      tokens <- resetUserSession(dbUser, credentials.deviceToken)
    } yield tokens
  }


  def resetUserSession(dbUser:UserT, newDeviceToken:String) = {
    for {
      _ <- updateDeviceToken(dbUser._id.toHexString, newDeviceToken)
      _ <- EitherT.liftF(repos.refreshTokenRepo().deleteForUserId(dbUser._id.toHexString))
      tokens <- generateNewTokens(dbUser.copy(deviceToken = newDeviceToken))
    } yield tokens
  }

  def refresh(refreshToken:AccessTokenRequest): SurfsUpEitherT[F, TokensWithUser] = {
    for {
      oldRefreshToken <- repos.refreshTokenRepo().getByRefreshToken(refreshToken.refreshToken).toRight(RefreshTokenNotFoundError())
      oldValidRefreshToken <- {
        val eitherT: SurfsUpEitherT[F, RefreshToken] =  {
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
      tokens <- generateNewTokens(user)
    } yield tokens
  }

  def generateNewTokens(user :UserT): SurfsUpEitherT[F, TokensWithUser] = {
    for {
      accessTokenId <- EitherT.pure(conversions.generateRandomString(10))
      token <- auth.createToken(user._id.toHexString, accessTokenId)
      newRefreshToken <- EitherT.liftF(repos.refreshTokenRepo().create(RefreshToken(user._id.toHexString, accessTokenId)))
      tokens <- auth.tokens(token.accessToken, newRefreshToken, token.expiredAt, user)
    } yield tokens
  }

  def updateUserProfile(id: String, name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long): SurfsUpEitherT[F, UserT] = {
    for {
      user <- getUser(id)
      operationResult <- updateUser(name, snoozeTill, disableAllAlerts, notificationsPerHour, user)
    } yield operationResult
  }

  private def updateUser(name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, user: UserT): SurfsUpEitherT[F, UserT] = {
    repos.usersRepo().update(user.copy(name = name, snoozeTill = snoozeTill, disableAllAlerts = disableAllAlerts, notificationsPerHour = notificationsPerHour)).toRight(CouldNotUpdateUserError())
  }

  def updateDeviceToken(id: String, deviceToken:String): SurfsUpEitherT[F, UserT] = {
    import cats.implicits._
    for {
      user <- getUser(id)
      operationResult <- repos.usersRepo().update(user.copy(deviceToken = deviceToken)).toRight(CouldNotUpdateUserError()).leftWiden[SurfsUpError]
    } yield operationResult
  }


  def logoutUser(userId: String): SurfsUpEitherT[F, Unit] = {
    for {
      _ <- EitherT.liftF(repos.refreshTokenRepo().deleteForUserId(userId))
      _ <- updateDeviceToken(userId, "")
    } yield ()
  }

  def doesNotExist(email: String, deviceType: String): SurfsUpEitherT[F, (Unit, Unit, Unit)] = {
    for {
      emailDoesNotExist <- countToEither(repos.credentialsRepo().count(email, deviceType))
      facebookDoesNotExist <- countToEither(repos.facebookCredentialsRepo().count(email, deviceType))
      appleDoesNotExist <- countToEither(repos.appleCredentialsRepository().count(email, deviceType))
    } yield (emailDoesNotExist, facebookDoesNotExist, appleDoesNotExist)
  }

  private def countToEither(count: F[Int]): SurfsUpEitherT[F, Unit] = {
    EitherT.liftF(count).flatMap(c => {
      val e: Either[UserAlreadyExistsError, Unit] = if (c > 0) Left(UserAlreadyExistsError("", ""))
      else Right(())
      EitherT.fromEither(e)
    })
  }

  def getUser(email: String, deviceType: String): SurfsUpEitherT[F, UserT] =
    OptionT(repos.usersRepo().getByEmailAndDeviceType(email, deviceType)).toRight(UserNotFoundError())

  def getUser(userId: String): SurfsUpEitherT[F, UserT] =
    OptionT(repos.usersRepo().getByUserId(userId)).toRight(UserNotFoundError())

  def createFeedback(feedback: Feedback): SurfsUpEitherT[F, Feedback] = {
    EitherT.liftF(repos.feedbackRepository.create(feedback))
  }

  def sendOtp(userId: String): SurfsUpEitherT[F, Unit] = {
    for {
      userFromDb <- getUser(userId)
      sent <- otpService.send(userFromDb._id.toHexString, userFromDb.email)
    } yield sent
  }

}

object UserService {
  def apply[F[_] : Sync](
                          repos: Repos[F],
                          userCredentialService: UserCredentialService[F],
                          otpService:OTPService[F],
                          authenticationService: AuthenticationService[F]
                        ): UserService[F] =
    new UserService(repos, userCredentialService, otpService, authenticationService)
}