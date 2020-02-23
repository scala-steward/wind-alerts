package com.uptech.windalerts.users

import java.io.FileInputStream

import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.alerts.AlertsRepository.FirestoreAlertsRepository
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.{HttpErrorHandler, secrets}
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
import org.mongodb.scala.MongoClient

import scala.util.Try

object UsersServer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = for {
    _ <- IO(getLogger.error("Starting"))

    projectId <- IO(sys.env("projectId"))
    credentials <- IO(Try(GoogleCredentials.fromStream(new FileInputStream(s"/app/resources/$projectId.json")))
      .getOrElse(GoogleCredentials.getApplicationDefault))
    options <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId(projectId).build)
    _ <- IO(FirebaseApp.initializeApp(options))
    db <- IO(FirestoreClient.getFirestore)

    client <- IO.pure(MongoClient(com.uptech.windalerts.domain.config.read.surfsUp.mongodb.url))
    mongoDb <- IO(client.getDatabase("surfsup").withCodecRegistry(com.uptech.windalerts.domain.codecs.mNotificationCodecRegistry))
    otpColl  <- IO( mongoDb.getCollection[OTPWithExpiry]("otp"))
    otpRepo <- IO( new MongoOtpRepository(otpColl))

    refreshTokensColl  <- IO( mongoDb.getCollection[RefreshToken]("refreshTokens"))
    refreshTokenRepo <- IO( new MongoRefreshTokenRepositoryAlgebra(refreshTokensColl))

    usersColl  <- IO( mongoDb.getCollection[UserT]("users"))
    userRepository <- IO( new MongoUserRepository(usersColl))

    credentialsCollection  <- IO( mongoDb.getCollection[Credentials]("credentials"))
    credentialsRepository <- IO( new MongoCredentialsRepository(credentialsCollection))

    androidPublisher <- IO(AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL))
    androidPurchaseRepoColl  <- IO( mongoDb.getCollection[AndroidPurchase]("androidPurchases"))

    androidPurchaseRepo <- IO( new MongoAndroidPurchaseRepository(androidPurchaseRepoColl))
    fbcredentialsCollection  <- IO( mongoDb.getCollection[FacebookCredentialsT]("facebookCredentials"))
    fbcredentialsRepository <- IO( new MongoFacebookCredentialsRepositoryAlgebra(fbcredentialsCollection))

    alertsRepository <- IO(new FirestoreAlertsRepository(db))
    usersService <- IO(new UserService(userRepository, credentialsRepository, fbcredentialsRepository, alertsRepository, secrets.read.surfsUp.facebook.key))
    auth <- IO(new Auth(refreshTokenRepo))
    endpoints <- IO(new UsersEndpoints(usersService, new HttpErrorHandler[IO], refreshTokenRepo, otpRepo, androidPurchaseRepo, auth, androidPublisher))
    httpApp <- IO(
      Router(
        "/v1/users" -> auth.middleware(endpoints.authedService()),
        "/v1/users" -> endpoints.openEndpoints(),
        "/v1/users/social/facebook" -> endpoints.socialEndpoints()
    ).orNotFound)
    server <- BlazeServerBuilder[IO]
                  .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
                  .withHttpApp(httpApp)
                  .serve
                  .compile
                  .drain
                  .as(ExitCode.Success)

  } yield server

}
