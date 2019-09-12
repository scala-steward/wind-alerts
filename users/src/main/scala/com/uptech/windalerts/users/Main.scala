package com.uptech.windalerts.users

import java.io.FileInputStream

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
  private val logger = getLogger

  logger.error("Starting")
  val credentials = GoogleCredentials.fromStream(new FileInputStream("wind-alerts-staging.json"))
  logger.error("Credentials")
  val options = new FirebaseOptions.Builder().setCredentials(credentials).setProjectId("wind-alerts-staging").build
  logger.error("Options")
  FirebaseApp.initializeApp(options)
  val auth = FirebaseAuth.getInstance
  logger.error("auth")

  val users = new Users.FireStoreBackedService(auth)
  logger.error("users " + users)

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
      logger.error("Called " + email + password)
      Ok(U.registerUser(email, password))
    }

  }.orNotFound



}