package com.uptech.windalerts.users

import java.io.FileInputStream

import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.alerts.AlertsRepository.FirestoreAlertsRepository
import com.uptech.windalerts.domain.domain.OTPWithExpiry
import com.uptech.windalerts.domain.{FirestoreOps, HttpErrorHandler, domain, secrets}
import com.uptech.windalerts.notifications.MongoNotificationsRepository
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}

import scala.util.Try

object UsersServer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- IO(getLogger.error("Starting"))

      projectId <- IO(sys.env("projectId"))
      credentials <- IO(Try(GoogleCredentials.fromStream(new FileInputStream(s"/app/resources/$projectId.json")))
        .getOrElse(GoogleCredentials.getApplicationDefault))
      options <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId(projectId).build)
      _ <- IO(FirebaseApp.initializeApp(options))
      db <- IO(FirestoreClient.getFirestore)
      credentialsRepository <- IO(new FirestoreCredentialsRepository(db, new FirestoreOps()))
      facebookCredentialsRepository <- IO(new FirestoreFacebookCredentialsRepositoryAlgebra(db))
      refreshTokenRepo <- IO(new FirestoreRefreshTokenRepository(db))
      dbOps <- IO(new FirestoreOps())
      client <- IO.pure(MongoClient(com.uptech.windalerts.domain.config.read.surfsUp.mongodb.url))
      mongoDb <- IO(client.getDatabase("surfsup").withCodecRegistry(com.uptech.windalerts.domain.codecs.mNotificationCodecRegistry))

      androidPublisher <- IO(AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL))
      coll  <- IO( mongoDb.getCollection[OTPWithExpiry]("otp"))

      otpRepo <- IO( new MongoOtpRepository(coll))
      userRepository <- IO(new FirestoreUserRepository(db, dbOps))
      alertsRepository <- IO(new FirestoreAlertsRepository(db))
      usersService <- IO(new UserService(userRepository, credentialsRepository, facebookCredentialsRepository, alertsRepository, secrets.read.surfsUp.facebook.key))
      refreshTokenRepository <- IO(new FirestoreRefreshTokenRepository(db))
      auth <- IO(new Auth(refreshTokenRepository))
      endpoints <- IO(new UsersEndpoints(usersService, new HttpErrorHandler[IO], refreshTokenRepo, otpRepo, auth, androidPublisher))
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

}
