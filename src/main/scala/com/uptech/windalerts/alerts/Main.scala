package com.uptech.windalerts.alerts

import java.io.FileInputStream

import cats.data.OptionT
import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.domain.HttpErrorHandler
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.status.{Beaches, Swells, Tides, Winds}
import com.uptech.windalerts.users.{FirestoreRefreshTokenRepository, FirestoreUserRepository, RefreshTokenRepositoryAlgebra}
import dev.profunktor.auth.JwtAuthMiddleware
import dev.profunktor.auth.jwt.{JwtAuth, JwtSecretKey}
import io.circe.parser._
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.{AuthedRoutes, HttpRoutes}
import org.log4s.getLogger
import pdi.jwt.{JwtAlgorithm, JwtClaim}

import scala.util.Try


object Main extends IOApp {

  private val logger = getLogger

  logger.error("Starting")

  case class UserId(id: String)


  val jwtAuth = JwtAuth(JwtSecretKey("secretKey"), JwtAlgorithm.HS256)

  val dbWithAuthIO = for {
    credentials <- IO(Try(GoogleCredentials.fromStream(new FileInputStream("/app/resources/wind-alerts-staging.json")))
      .getOrElse(GoogleCredentials.getApplicationDefault))
    options <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId("wind-alerts-staging").build)
    _ <- IO(FirebaseApp.initializeApp(options))
    db <- IO(FirestoreClient.getFirestore)
    notifications <- IO(FirebaseMessaging.getInstance)
  } yield (db, notifications)

  val dbWithAuth = dbWithAuthIO.unsafeRunSync()

  val beaches = Beaches.ServiceImpl(Winds.impl, Swells.impl, Tides.impl)
  val alertsRepo: AlertsRepository.Repository = new AlertsRepository.FirebaseBackedRepository(dbWithAuth._1)

  val alertService = new AlertsService.ServiceImpl(alertsRepo)
  val usersRepo = new FirestoreUserRepository(dbWithAuth._1)

  val refreshTokenRepositoryAlgebra: RefreshTokenRepositoryAlgebra = new FirestoreRefreshTokenRepository(dbWithAuth._1)
  implicit val httpErrorHandler: HttpErrorHandler[IO] = new HttpErrorHandler[IO]


  val authenticate: JwtClaim => IO[Option[UserId]] = {
    claim => {
      val r = for {
        parseResult <- IO.fromEither(parse(claim.content))
        accessTokenId <- IO.fromEither(parseResult.hcursor.downField("accessTokenId").as[String])
        maybeRefreshToken <- refreshTokenRepositoryAlgebra.getByAccessTokenId(accessTokenId).value
      } yield maybeRefreshToken

      r.map(f=>f.map(t=>UserId(t.userId)))
    }

  }

  val middleware = JwtAuthMiddleware[IO, UserId](jwtAuth, authenticate)


  def authedService: AuthedRoutes[UserId, IO] =
    AuthedRoutes {
      case GET -> Root / "alerts" as user => {
        val resp = alertService.getAllForUser(user.id)
        val either = resp.attempt.unsafeRunSync()
        val response = either.fold(httpErrorHandler.handleThrowable, _ => Ok(either.right.get))
        OptionT.liftF(response)
      }

      case authReq@POST -> Root / "alerts" as user => {
        val response = authReq.req.decode[AlertRequest] { alert =>
          val saved = alertService.save(alert, user.id)
          val either = saved.attempt.unsafeRunSync()
          either.fold(httpErrorHandler.handleThrowable, _ => Created(either.right.get))
        }
        OptionT.liftF(response)
      }

      case DELETE -> Root / "alerts" / alertId as user => {
        val eitherDeleted = alertService.delete(user.id, alertId)
        val eitherDeletedUnsafe = eitherDeleted.attempt.unsafeRunSync()
        val response = if (eitherDeletedUnsafe.isLeft) {
          httpErrorHandler.handleThrowable(eitherDeletedUnsafe.left.get)
        } else {
          eitherDeletedUnsafe.right.get match {
            case Left(value) => httpErrorHandler.handleThrowable(value)
            case Right(_) => NoContent()
          }
        }

        OptionT.liftF(response)
      }

      case authReq@PUT -> Root / "alerts" / alertId as user => {
        val response = authReq.req.decode[AlertRequest] { alert =>
          val updated = alertService.update(user.id, alertId, alert)
          val resp = updated.unsafeRunSync()
          Ok(resp.toOption.get.unsafeRunSync())
        }
        OptionT.liftF(response)
      }

    }

  def securedRoutes: HttpRoutes[IO] = middleware(authedService)

  val  httpApp = Router(
    "/v1/users" -> securedRoutes
  ).orNotFound


  def run(args: List[String]): IO[ExitCode] = {
    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }

}
