package com.uptech.windalerts.users

import java.io.FileInputStream
import java.util.concurrent.TimeUnit

import cats.data.EitherT
import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain.{Credentials, RegisterRequest}
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import scala.util.Try

object UsersServer extends IOApp {
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
      case req@POST -> Root / "login" =>
        val action = for {
          login <- EitherT.liftF(req.as[Credentials])
          token <- userService.getByCredentials(login).map(c => createToken(c.email, 1))
        } yield token

        action.value.flatMap {
          case Right((token)) => Ok(token)
          case Left(UserAuthenticationFailedError(name)) =>
            BadRequest(s"Authentication failed for user $name")
        }

    }


  def createToken(username: String, expirationPeriodInDays: Int): String = {
    val claims = JwtClaim(
      expiration = Some(System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(expirationPeriodInDays)),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      issuer = Some("wind-alerts.com"),
      subject = Some(username)
    )

    Jwt.encode(claims, "secretKey", JwtAlgorithm.HS256)
  }

  private val service = new UserService(new FirestaoreUserRepository(db), new FirestoreCredentialsRepository(db))
  val httpApp = Router(
    "/v1/users" -> signupEndpoint(service)
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
