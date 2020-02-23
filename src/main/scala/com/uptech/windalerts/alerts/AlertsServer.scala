package com.uptech.windalerts.alerts

import java.io.FileInputStream

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.domain.domain.{Credentials, OTPWithExpiry, RefreshToken, UserT}
import com.uptech.windalerts.domain.{FirestoreOps, HttpErrorHandler, secrets}
import com.uptech.windalerts.users.{Auth, FirestoreFacebookCredentialsRepositoryAlgebra, MongoCredentialsRepository, MongoOtpRepository, MongoRefreshTokenRepositoryAlgebra, MongoUserRepository, UserService}
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
import org.mongodb.scala.MongoClient

import scala.util.Try

object AlertsServer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- IO(getLogger.error("Starting"))
      projectId <- IO(sys.env("projectId"))
      credentials <- IO(Try(GoogleCredentials.fromStream(new FileInputStream(s"/app/resources/$projectId.json")))
        .getOrElse(GoogleCredentials.getApplicationDefault))
      options <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId(projectId).build)
      _ <- IO(FirebaseApp.initializeApp(options))
      db <- IO(FirestoreClient.getFirestore)
      alertsRepo <- IO(new AlertsRepository.FirestoreAlertsRepository(db))
      alertService <- IO(new AlertsService.ServiceImpl(alertsRepo))
      httpErrorHandler <- IO(new HttpErrorHandler[IO])
      fbCredentialsRepository <- IO(new FirestoreFacebookCredentialsRepositoryAlgebra(db))

      client <- IO.pure(MongoClient(com.uptech.windalerts.domain.config.read.surfsUp.mongodb.url))
      mongoDb <- IO(client.getDatabase("surfsup").withCodecRegistry(com.uptech.windalerts.domain.codecs.mNotificationCodecRegistry))
      refreshTokensCollection  <- IO( mongoDb.getCollection[RefreshToken]("refreshTokens"))
      refreshTokensRepo <- IO( new MongoRefreshTokenRepositoryAlgebra(refreshTokensCollection))
      usersCollection  <- IO( mongoDb.getCollection[UserT]("users"))
      userRepository <- IO( new MongoUserRepository(usersCollection))
      credentialsCollection  <- IO( mongoDb.getCollection[Credentials]("credentials"))
      credentialsRepository <- IO( new MongoCredentialsRepository(credentialsCollection))

      auth <- IO(new Auth(refreshTokensRepo))
      usersService <- IO( new UserService(userRepository, credentialsRepository, fbCredentialsRepository, alertsRepo, secrets.read.surfsUp.facebook.key))
      alertsEndPoints <- IO(new AlertsEndpoints(alertService, usersService, auth, httpErrorHandler))
      httpApp <- IO(Router(
        "/v1/users/alerts" -> auth.middleware(alertsEndPoints.allUsersService())
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
