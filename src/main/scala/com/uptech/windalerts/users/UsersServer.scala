package com.uptech.windalerts.users

import java.io.FileInputStream

import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain.RegisterRequest
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger

import scala.util.Try
object UsersServer  extends IOApp {
  private val logger = getLogger

  logger.error("Starting")


  val dbIO = for {
    credentials <- IO(Try(GoogleCredentials.fromStream(new FileInputStream("/app/resources/wind-alerts-staging.json")))
      .getOrElse(GoogleCredentials.getApplicationDefault))
    options <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId("wind-alerts-staging").build)
    _ <- IO(FirebaseApp.initializeApp(options))
    db <- IO(FirestoreClient.getFirestore)
  } yield db

  val db = dbIO.unsafeRunSync()

  val credentialsRepositoryAlgebra = new FirestoreCredentialsRepository(db)
  private def signupEndpoint(
                              userService: UserService
                            ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case req@POST -> Root =>
        val action = for {
          rr <- req.as[RegisterRequest]
          result <- userService.createUser(rr).value
        } yield result

        action.flatMap {
          case Right(saved) => Ok(saved)
          case Left(UserAlreadyExistsError(email, deviceType)) =>
            Conflict(s"The user with email ${email} for device tyepe ${deviceType} already exists")
        }
    }

  val  httpApp = Router(
    "/v1/users" -> signupEndpoint(new UserService(new FirestaoreUserRepository(db), new FirestoreCredentialsRepository(db)))
  ).orNotFound

  override def run(args: List[String]): IO[ExitCode] = {
    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)

  }
}
