package com.uptech.windalerts.alerts

import java.io.FileInputStream

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.domain.HttpErrorHandler
import com.uptech.windalerts.users.{Auth, FirestoreCredentialsRepository, FirestoreRefreshTokenRepository, RefreshTokenRepositoryAlgebra}
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger

import scala.util.Try

object AlertsServer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- IO(getLogger.error("Starting"))
      credentials <- IO(Try(GoogleCredentials.fromStream(new FileInputStream("/app/resources/wind-alerts-staging.json")))
        .getOrElse(GoogleCredentials.getApplicationDefault))
      options <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId("wind-alerts-staging").build)
      _ <- IO(FirebaseApp.initializeApp(options))
      db <- IO(FirestoreClient.getFirestore)
      alertsRepo <- IO(new AlertsRepository.FirebaseBackedRepository(db))
      alertService <- IO(new AlertsService.ServiceImpl(alertsRepo))
      httpErrorHandler <- IO(new HttpErrorHandler[IO])
      refreshTokenRepository <- IO(new FirestoreRefreshTokenRepository(db))
      auth <- IO(new Auth(refreshTokenRepository))
      httpApp <- IO(Router(
        "/v1/users/alerts" -> auth.middleware(new AlertsEndpoints(alertService, httpErrorHandler).authedService())
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
