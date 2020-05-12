package com.uptech.windalerts.users

import java.security.PrivateKey

import cats.data.{EitherT, OptionT}
import cats.effect.{IO, Sync}
import com.github.t3hnar.bcrypt._
import com.google.api.services.androidpublisher.AndroidPublisher
import com.restfb.{DefaultFacebookClient, Parameter, Version}
import com.softwaremill.sttp.{HttpURLConnectionBackend, sttp, _}
import com.uptech.windalerts.alerts.AlertsRepositoryT
import com.uptech.windalerts.domain.domain.UserType._
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.{domain, secrets}
import io.circe.syntax._
import org.mongodb.scala.bson.ObjectId
import com.uptech.windalerts.domain.codecs._
import io.circe.optics.JsonPath.root
import io.circe.parser
import io.scalaland.chimney.dsl._

class UserService[F[_] : Sync](userRepo: UserRepositoryAlgebra[F],
                               credentialsRepo: CredentialsRepositoryAlgebra[F],
                               appleCredentialsRepo: AppleCredentialsRepository[F],
                               facebookCredentialsRepo: FacebookCredentialsRepositoryAlgebra[F],
                               alertsRepository: AlertsRepositoryT[F],
                               feedbackRepository: FeedbackRepository[F],
                               facebookSecretKey: String,
                               androidPublisher: AndroidPublisher,
                               applePrivateKey:PrivateKey) {

  def verifyEmail(id: String) = {
    def makeUserTrial(user: UserT): EitherT[F, ValidationError, UserT] = {
      userRepo.update(user.copy(
        userType = Trial.value,
        startTrialAt = System.currentTimeMillis(),
        endTrialAt = System.currentTimeMillis() + (30L * 24L * 60L * 60L * 1000L),
      )).toRight(CouldNotUpdateUserError())
    }

    def startTrial(id: String): EitherT[F, ValidationError, UserT] = {
      for {
        user <- getUser(id)
        operationResult <- makeUserTrial(user)
      } yield operationResult
    }

    for {
      operationResult <- startTrial(id)
    } yield operationResult
  }


  def updateSubscribedUserRole(user: UserId, startTime: Long, expiryTime: Long) = {
    def withTypeFixed(start: Long, expiry: Long, user: UserT): EitherT[F, ValidationError, UserT] = {
      userRepo.update(user.copy(userType = Premium.value, lastPaymentAt = start, nextPaymentAt = expiry)).toRight(CouldNotUpdateUserError())
    }

    def makeUserPremium(id: String, start: Long, expiry: Long):EitherT[F, ValidationError, UserT] = {
      for {
        user <- getUser(id)
        operationResult <- withTypeFixed(start, expiry, user)
      } yield operationResult
    }

    def makeUserPremiumExpired(id: String): EitherT[F, ValidationError, UserT] = {
      for {
        user <- getUser(id)
        operationResult <- userRepo.update(user.copy(userType = PremiumExpired.value, nextPaymentAt = -1)).toRight(CouldNotUpdateUserError())
        r <- EitherT.liftF(alertsRepository.disableAllButOneAlerts(id))
      } yield operationResult
    }

    if (expiryTime > System.currentTimeMillis()) {
      makeUserPremium(user.id, startTime, expiryTime)
    } else {
      makeUserPremiumExpired(user.id)
    }
  }


  def getUserAndUpdateRole(userId: String): EitherT[F, UserNotFoundError, UserT] = {
    for {
      eitherUser <- OptionT(userRepo.getByUserId(userId)).toRight(UserNotFoundError())
      updated <- updateRole(eitherUser)
    } yield updated
  }

  def getUserAndUpdateRole(email: String, deviceType: String): EitherT[F, UserNotFoundError, UserT] = {
    for {
      eitherUser <- OptionT(userRepo.getByEmailAndDeviceType(email, deviceType)).toRight(UserNotFoundError())
      updated <- updateRole(eitherUser)
    } yield updated
  }

  private def updateRole(eitherUser: UserT): EitherT[F, UserNotFoundError, UserT] = {
    if (UserType(eitherUser.userType) == Trial && eitherUser.isTrialEnded()) {
      for {
        updated <- update(eitherUser.copy(userType = UserType.TrialExpired.value, lastPaymentAt = -1, nextPaymentAt = -1))
        _ <- EitherT.liftF(alertsRepository.disableAllButOneAlerts(updated._id.toHexString))
      } yield updated
    } else {
      EitherT.fromEither(toEither(eitherUser))
    }
  }

  def updateUserProfile(id: String, name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long): EitherT[F, ValidationError, UserT] = {
    for {
      user <- getUser(id)
      operationResult <- updateUser(name, snoozeTill, disableAllAlerts, notificationsPerHour, user)
    } yield operationResult
  }

  private def updateUser(name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, user: UserT): EitherT[F, ValidationError, UserT] = {
    userRepo.update(user.copy(name = name, snoozeTill = snoozeTill, disableAllAlerts = disableAllAlerts, notificationsPerHour = notificationsPerHour, lastPaymentAt = -1, nextPaymentAt = -1)).toRight(CouldNotUpdateUserError())
  }

  def updateDeviceToken(userId: String, deviceToken: String) =
    userRepo.updateDeviceToken(userId, deviceToken)

  def updatePassword(userId: String, password: String): OptionT[F, Unit] =
    credentialsRepo.updatePassword(userId, password.bcrypt)

  def getFacebookUserByAccessToken(accessToken: String, deviceType: String) = {
    for {
      facebookClient <- EitherT.pure((new DefaultFacebookClient(accessToken, facebookSecretKey, Version.LATEST)))
      facebookUser <- EitherT.pure((facebookClient.fetchObject("me", classOf[com.restfb.types.User], Parameter.`with`("fields", "name,id,email"))))
      dbUser <- getUser(facebookUser.getEmail, deviceType)
    } yield dbUser
  }

  def createUser(rr: FacebookRegisterRequest): EitherT[F, UserAlreadyExistsError, (UserT, FacebookCredentialsT)] = {
    for {
      facebookClient <- EitherT.pure(new DefaultFacebookClient(rr.accessToken, facebookSecretKey, Version.LATEST))
      facebookUser <- EitherT.pure((facebookClient.fetchObject("me", classOf[com.restfb.types.User], Parameter.`with`("fields", "name,id,email"))))
      _ <- doesNotExist(facebookUser.getEmail, rr.deviceType)

      savedCreds <- EitherT.liftF(facebookCredentialsRepo.create(FacebookCredentialsT(facebookUser.getEmail, rr.accessToken, rr.deviceType)))
      savedUser <- EitherT.liftF(userRepo.create(UserT.create(new ObjectId(savedCreds._id.toHexString), facebookUser.getEmail, facebookUser.getName, rr.deviceId, rr.deviceToken, rr.deviceType, System.currentTimeMillis(), Trial.value, -1, false, 4)))
    } yield (savedUser, savedCreds)
  }

  def createUser(rr: AppleRegisterRequest): EitherT[F, UserAlreadyExistsError, (UserT, AppleCredentials)] = {
    for {
      appleUser <- EitherT.pure(AppleLogin.getUser(rr.authorizationCode, applePrivateKey))
      _ <- doesNotExist(appleUser.email, rr.deviceType)

      savedCreds <- EitherT.liftF(appleCredentialsRepo.create(AppleCredentials(appleUser.email, rr.deviceType, appleUser.sub)))
      savedUser <- EitherT.liftF(userRepo.create(UserT.create(new ObjectId(savedCreds._id.toHexString), appleUser.email, rr.name, rr.deviceId, rr.deviceToken, rr.deviceType, System.currentTimeMillis(), Trial.value, -1, false, 4)))
    } yield (savedUser, savedCreds)
  }

  def loginUser(rr: AppleLoginRequest) = {
    for {
      appleUser <- EitherT.pure(AppleLogin.getUser(rr.authorizationCode, applePrivateKey))
      _ <- appleCredentialsRepo.findByAppleId(appleUser.sub)
      dbUser <- getUser(appleUser.email, rr.deviceType)
    } yield dbUser
  }

  def createUser(rr: RegisterRequest): EitherT[F, UserAlreadyExistsError, UserT] = {
    val credentials = Credentials(rr.email, rr.password.bcrypt, rr.deviceType)
    for {
      _ <- doesNotExist(credentials.email, credentials.deviceType)
      savedCreds <- EitherT.liftF(credentialsRepo.create(credentials))
      saved <- EitherT.liftF(userRepo.create(UserT.create(new ObjectId(savedCreds._id.toHexString), rr.email, rr.name, rr.deviceId, rr.deviceToken, rr.deviceType, -1, Registered.value, -1, false, 4)))
    } yield saved
  }


  def doesNotExist(email: String, deviceType: String) = {
    for {
      emailDoesNotExist <- countToEither(credentialsRepo.count(email, deviceType))
      facebookDoesNotExist <- countToEither(facebookCredentialsRepo.count(email, deviceType))
      appleDoesNotExist <- countToEither(appleCredentialsRepo.count(email, deviceType))
    } yield (emailDoesNotExist, facebookDoesNotExist, appleDoesNotExist)
  }

  private def countToEither(count: F[Int]) = {
    val fbccdentialDoesNotExist: EitherT[F, UserAlreadyExistsError, Unit] = EitherT.liftF(count).flatMap(c => {
      val e: Either[UserAlreadyExistsError, Unit] = if (c > 0) Left(UserAlreadyExistsError("", ""))
      else Right(())
      EitherT.fromEither(e)
    })
    fbccdentialDoesNotExist
  }


  private def toEither(user: UserT): Either[UserNotFoundError, UserT] = {
    Right(user)
  }

  def getUser(email: String, deviceType: String): EitherT[F, UserNotFoundError, UserT] =
    OptionT(userRepo.getByEmailAndDeviceType(email, deviceType)).toRight(UserNotFoundError())

  def getUser(userId: String): EitherT[F, ValidationError, UserT] =
    OptionT(userRepo.getByUserId(userId)).toRight(UserNotFoundError())

  def getByCredentials(
                        email: String, password: String, deviceType: String
                      ): EitherT[F, ValidationError, Credentials] =
    for {
      creds <- credentialsRepo.findByCreds(email, deviceType).toRight(UserAuthenticationFailedError(email))
      passwordMatched <- isPasswordMatch(password, creds)
    } yield passwordMatched


  private def isPasswordMatch(password: String, creds: Credentials): EitherT[F, ValidationError, Credentials] = {
    val passwordMatch =
      if (password.isBcrypted(creds.password)) {
        Right(creds)
      } else {
        Left(UserAuthenticationFailedError(creds.email))
      }
    EitherT.fromEither(passwordMatch)
  }

  def update(user: UserT): EitherT[F, UserNotFoundError, UserT] =
    for {
      saved <- userRepo.update(user).toRight(UserNotFoundError())
    } yield saved


  def getAndroidPurchase(request: AndroidReceiptValidationRequest): EitherT[F, ValidationError, domain.SubscriptionPurchase] = {
    getAndroidPurchase(request.productId, request.token)
  }

  def getAndroidPurchase(productId: String, token: String): EitherT[F, ValidationError, domain.SubscriptionPurchase] = {
    EitherT.pure({
      androidPublisher.purchases().subscriptions().get(ApplicationConfig.PACKAGE_NAME, productId, token).execute().into[domain.SubscriptionPurchase].enableBeanGetters
        .withFieldComputed(_.expiryTimeMillis, _.getExpiryTimeMillis.toLong)
        .withFieldComputed(_.startTimeMillis, _.getStartTimeMillis.toLong).transform
    })
  }

  def getApplePurchase(receiptData: String, password: String): EitherT[IO, ValidationError, AppleSubscriptionPurchase] = {
    implicit val backend = HttpURLConnectionBackend()

    val json = ApplePurchaseVerificationRequest(receiptData, password, true).asJson.toString()
    val req = sttp.body(json).contentType("application/json")
      .post(uri"https://sandbox.itunes.apple.com/verifyReceipt")

    EitherT.fromEither(
      req
        .send().body
        .left.map(UnknownError(_))
        .flatMap(parser.parse(_))
        .map(root.receipt.in_app.each.json.getAll(_))
        .flatMap(_.map(p => p.as[AppleSubscriptionPurchase])
          .filter(_.isRight).maxBy(_.right.get.expires_date_ms))
        .left.map(e => UnknownError(e.getMessage))
    )
  }

  def createFeedback(feedback:Feedback):EitherT[F, ValidationError, Feedback] = {
    EitherT.liftF(feedbackRepository.create(feedback))
  }

}

object UserService {
  def apply[F[_] : Sync](
                          usersRepository: UserRepositoryAlgebra[F],
                          credentialsRepository: CredentialsRepositoryAlgebra[F],
                          appleRepository: AppleCredentialsRepository[F],
                          facebookCredentialsRepositoryAlgebra: FacebookCredentialsRepositoryAlgebra[F],
                          alertsRepository: AlertsRepositoryT[F],
                          feedbackRepository: FeedbackRepository[F],

                          androidPublisher: AndroidPublisher,
                          applePrivateKey:PrivateKey
                        ): UserService[F] =
    new UserService(usersRepository, credentialsRepository, appleRepository, facebookCredentialsRepositoryAlgebra, alertsRepository, feedbackRepository, secrets.read.surfsUp.facebook.key, androidPublisher, applePrivateKey)
}