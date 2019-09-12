package com.uptech.windalerts.users

import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import com.uptech.windalerts.domain.Domain
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger

object Main extends IOApp {

  import com.uptech.windalerts.domain.DomainCodec._

  val credentials = GoogleCredentials.getApplicationDefault
  val options = new FirebaseOptions.Builder().setCredentials(credentials).setProjectId("wind-alerts-staging").build
  FirebaseApp.initializeApp(options)
  val auth = FirebaseAuth.getInstance
  val users = new Users.FireStoreBackedService(auth)

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withHttpApp(sendAlertsRoute(users))
      .serve
      .compile
      .drain
      .as(ExitCode.Success)

  def sendAlertsRoute(U: Users.Service) = HttpRoutes.of[IO] {

    case GET -> Root / "users" / email / password => {
      Ok(U.registerUser(email, password))
    }

  }.orNotFound



}