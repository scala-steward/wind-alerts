package com.uptech.windalerts.users

import cats.data.{EitherT, OptionT}
import cats.effect.Sync
import com.github.t3hnar.bcrypt._
import com.restfb.types.User
import com.restfb.{DefaultFacebookClient, Parameter, Version}
import com.uptech.windalerts.Repos
import com.uptech.windalerts.domain._
import com.uptech.windalerts.domain.domain.UserType._
import com.uptech.windalerts.domain.domain.{Credentials, SurfsUpEitherT, _}
import org.mongodb.scala.bson.ObjectId

class UserService[F[_] : Sync](repos: Repos[F], otpService: OTPService[F], auth: AuthenticationService[F]) {

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
      saved <- EitherT.liftF(repos.usersRepo().create(UserT.create(new ObjectId(savedCreds._id.toHexString), rr.email, rr.name,  rr.deviceToken, rr.deviceType, -1, Registered.value, -1, false, 4)))
    } yield (saved, savedCreds)
  }


  def login(credentials:LoginRequest): SurfsUpEitherT[F, TokensWithUser] = {
    for {
      dbCredentials <- getByCredentials(credentials.email, credentials.password, credentials.deviceType)
      dbUser <- getUser(dbCredentials.email, dbCredentials.deviceType)
      tokens <- resetUserSession(dbUser, credentials.deviceToken)
    } yield tokens
  }

  def registerOrLoginFacebookUser(credentials:FacebookRegisterRequest): SurfsUpEitherT[F, TokensWithUser] = {
    for {
      facebookClient <- EitherT.pure(new DefaultFacebookClient(credentials.accessToken, repos.fbSecret(), Version.LATEST))
      facebookUser <- EitherT.pure(facebookClient.fetchObject("me", classOf[com.restfb.types.User], Parameter.`with`("fields", "name,id,email")))
      exisitngCredentials <- EitherT.liftF(repos.facebookCredentialsRepo().find(facebookUser.getEmail, credentials.deviceType))
      tokens <- {
        if (exisitngCredentials.isEmpty) {
          for {
            _ <- doesNotExist(facebookUser.getEmail, credentials.deviceType)
            result <- createUser(credentials, facebookUser)
            tokens <- generateNewTokens(result._1)
          } yield tokens
        } else {
          for {
            dbUser <- getUser(facebookUser.getEmail, credentials.deviceType)
            tokens <- resetUserSession(dbUser, credentials.deviceToken)
          } yield tokens
        }
      }
    } yield tokens
  }

  def registerOrLoginAppleUser(rr:AppleRegisterRequest): SurfsUpEitherT[F, TokensWithUser] = {
    for {
      appleUser <- EitherT.pure(AppleLogin.getUser(rr.authorizationCode, repos.appleLoginConf()))
      exisitngCredentials <- EitherT.liftF(repos.appleCredentialsRepository().find(appleUser.email, rr.deviceType))

      tokens <- {
        if (exisitngCredentials.isEmpty) {
          for {
            _ <- doesNotExist(appleUser.email, rr.deviceType)
            result <- createUser(rr, appleUser)
            tokens <- generateNewTokens(result._1)
          } yield tokens
        } else {
          for {
            dbUser <- getUser(appleUser.email, rr.deviceType)
            tokens <- resetUserSession(dbUser, rr.deviceToken)
          } yield tokens
        }
      }
    } yield tokens
  }

  def resetUserSession(dbUser:UserT, deviceToken:String) = {
    for {
      _ <- updateDeviceToken(dbUser._id.toHexString, deviceToken)
      _ <- EitherT.liftF(repos.refreshTokenRepo().deleteForUserId(dbUser._id.toHexString))
      tokens <- generateNewTokens(dbUser)
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

  def createUser(rr: FacebookRegisterRequest, facebookUser:User): SurfsUpEitherT[F, (UserT, FacebookCredentialsT)] = {
    for {
      savedCreds <- EitherT.liftF(repos.facebookCredentialsRepo().create(FacebookCredentialsT(facebookUser.getEmail, rr.accessToken, rr.deviceType)))
      savedUser <- EitherT.liftF(repos.usersRepo().create(UserT.create(new ObjectId(savedCreds._id.toHexString), facebookUser.getEmail, facebookUser.getName, rr.deviceToken, rr.deviceType, System.currentTimeMillis(), Trial.value, -1, false, 4)))
    } yield (savedUser, savedCreds)
  }

  def createUser(rr: AppleRegisterRequest, appleUser: AppleUser): SurfsUpEitherT[F, (UserT, AppleCredentials)] = {
    for {
      savedCreds <- EitherT.liftF(repos.appleCredentialsRepository().create(AppleCredentials(appleUser.email, rr.deviceType, appleUser.sub)))
      savedUser <- EitherT.liftF(repos.usersRepo().create(UserT.create(new ObjectId(savedCreds._id.toHexString), appleUser.email,
        rr.name,  rr.deviceToken, rr.deviceType, System.currentTimeMillis(), Trial.value, -1, false, 4)))
    } yield (savedUser, savedCreds)
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

  def updateDeviceToken(userId: String, deviceToken: String): SurfsUpEitherT[F, Unit] =
    repos.usersRepo().updateDeviceToken(userId, deviceToken).toRight(CouldNotUpdateUserDeviceError())

  def changePassword(request:ChangePasswordRequest): SurfsUpEitherT[F, Unit] = {
    for {
      credentials <- getByCredentials(request.email, request.oldPassword, request.deviceType)
      result <- updatePassword(credentials._id.toHexString, credentials.password)
    } yield result
  }

  def updatePassword(userId: String, password: String): SurfsUpEitherT[F, Unit] = {
    for {
      _ <- repos.credentialsRepo().updatePassword(userId, password.bcrypt).toRight(CouldNotUpdatePasswordError())
      result <- EitherT.liftF(repos.refreshTokenRepo().deleteForUserId(userId))
    } yield result
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

  def getByCredentials(
                        email: String, password: String, deviceType: String
                      ): SurfsUpEitherT[F, Credentials] =
    for {
      creds <- repos.credentialsRepo().findByCreds(email, deviceType).toRight(UserAuthenticationFailedError(email))
      passwordMatched <- isPasswordMatch(password, creds)
    } yield passwordMatched

  private def isPasswordMatch(password: String, creds: Credentials): SurfsUpEitherT[F, Credentials] = {
    EitherT.fromEither(if (password.isBcrypted(creds.password)) {
      Right(creds)
    } else {
      Left(UserAuthenticationFailedError(creds.email))
    })
  }

  def resetPassword(
                     email: String, deviceType: String
                   ): SurfsUpEitherT[F, Credentials] =
    for {
      creds <- repos.credentialsRepo().findByCreds(email, deviceType).toRight(UserAuthenticationFailedError(email))
      newPassword <- EitherT.pure(conversions.generateRandomString(10))
      _ <- updatePassword(creds._id.toHexString, newPassword)
      _ <- EitherT.liftF(repos.refreshTokenRepo().deleteForUserId(creds._id.toHexString))
      user <- EitherT.liftF(repos.usersRepo().getByUserId(creds._id.toHexString))
      _ <- EitherT.pure(repos.emailConf().sendResetPassword(user.get.firstName(), email, newPassword))
    } yield creds

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
                          otpService:OTPService[F],
                          authenticationService: AuthenticationService[F]
                        ): UserService[F] =
    new UserService(repos, otpService, authenticationService)
}