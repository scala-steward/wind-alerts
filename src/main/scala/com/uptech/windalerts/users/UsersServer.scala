package com.uptech.windalerts.users

import java.io.FileInputStream

import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.domain.HttpErrorHandler
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger

import scala.util.Try

object UsersServer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- IO(getLogger.error("Starting"))

      credentials <- IO(Try(GoogleCredentials.fromStream(new FileInputStream("/app/resources/wind-alerts-staging.json"))).getOrElse(GoogleCredentials.getApplicationDefault))
      options <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId("wind-alerts-staging").build)
      _ <- IO(FirebaseApp.initializeApp(options))
      db <- IO(FirestoreClient.getFirestore)
      credentialsRepository <- IO(new FirestoreCredentialsRepository(db))
      refreshTokenRepo <- IO(new FirestoreRefreshTokenRepository(db))
      usersService <- IO(new UserService(new FirestoreUserRepository(db), credentialsRepository))
      refreshTokenRepository <- IO(new FirestoreRefreshTokenRepository(db))
      auth <- IO(new Auth(refreshTokenRepository))
      endpoints <- IO(new UsersEndpoints(usersService, new HttpErrorHandler[IO], refreshTokenRepo, auth))
      httpApp <- IO(Router("/v1/users" -> endpoints.openEndpoints()).orNotFound)
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
