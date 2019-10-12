package com.uptech.windalerts.notifications

import java.io.FileInputStream

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.alerts.{AlertsRepository, AlertsService}
import com.uptech.windalerts.domain.HttpErrorHandler
import com.uptech.windalerts.status.{Beaches, Swells, Tides, Winds}
import com.uptech.windalerts.users.{FirestoreUserRepository, UserRepositoryAlgebra}
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io.{->, /, GET, Ok, _}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger

import scala.util.Try

object SendNotifications extends IOApp {

  private val logger = getLogger

  logger.error("Starting")


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

  val alerts = new AlertsService.ServiceImpl(alertsRepo)
  val usersRepo = new FirestoreUserRepository(dbWithAuth._1)

  implicit val httpErrorHandler: HttpErrorHandler[IO] = new HttpErrorHandler[IO]
  val notifications = new Notifications(alerts, beaches, usersRepo, dbWithAuth._2, httpErrorHandler, new FirestoreNotificationRepository(dbWithAuth._1))

  def allRoutes(A: AlertsService.Service, B: Beaches.Service, UR: UserRepositoryAlgebra, firebaseMessaging: FirebaseMessaging, H: HttpErrorHandler[IO]) = HttpRoutes.of[IO] {
    case GET -> Root / "notify" => {
      val res = notifications.sendNotification
      val either = res.unsafeRunSync()

//      either.head match {
//        case Left(e) => H.handleThrowable(e)
//        case Right(value) => Ok(value)
//      }
      Ok()
    }
  }.orNotFound


  def run(args: List[String]): IO[ExitCode] = {

    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withHttpApp(allRoutes(alerts, beaches, usersRepo, dbWithAuth._2, httpErrorHandler))
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }

}
