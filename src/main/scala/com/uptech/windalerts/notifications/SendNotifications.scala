package com.uptech.windalerts.notifications

import java.io.{File, FileInputStream}

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.alerts.{AlertsRepository, AlertsService}
import com.uptech.windalerts.domain.{AppSettings, FirestoreOps, HttpErrorHandler}
import com.uptech.windalerts.status.{Beaches, Swells, Tides, Winds}
import com.uptech.windalerts.users.{FirestoreUserRepository, UserRepositoryAlgebra}
import io.circe.config.parser
import io.circe.generic.auto._
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
    projectId <- IO(sys.env("projectId"))
    credentials <- IO(Try(GoogleCredentials.fromStream(new FileInputStream(s"/app/resources/$projectId.json")))
      .getOrElse(GoogleCredentials.getApplicationDefault))
    options <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId(projectId).build)
    _ <- IO(FirebaseApp.initializeApp(options))
    db <- IO(FirestoreClient.getFirestore)
    notifications <- IO(FirebaseMessaging.getInstance)
  } yield (db, notifications)

  val dbWithAuth = dbWithAuthIO.unsafeRunSync()

  val conf = readConf
  val key = conf.surfsup.willyWeather.key
  val beaches = Beaches.ServiceImpl(Winds.impl(key), Swells.impl(key), Tides.impl(key))
  val alertsRepo: AlertsRepository.Repository = new AlertsRepository.FirestoreAlertsRepository(dbWithAuth._1)

  val alerts = new AlertsService.ServiceImpl(alertsRepo)
  val usersRepo = new FirestoreUserRepository(dbWithAuth._1, new FirestoreOps())

  implicit val httpErrorHandler: HttpErrorHandler[IO] = new HttpErrorHandler[IO]
  val notifications = new Notifications(alerts, beaches, usersRepo, dbWithAuth._2, httpErrorHandler, new FirestoreNotificationRepository(dbWithAuth._1, new FirestoreOps()))

  def allRoutes(A: AlertsService.Service, B: Beaches.Service, UR: UserRepositoryAlgebra, firebaseMessaging: FirebaseMessaging, H: HttpErrorHandler[IO]) = HttpRoutes.of[IO] {
    case GET -> Root / "notify" => {
      val res = notifications.sendNotification
      val either = res.unsafeRunSync()
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

  private def readConf: AppSettings = {
    val projectId = sys.env("projectId")
    Option(parser.decodeFile[AppSettings](new File(s"/app/resources/$projectId.conf")).toOption
      .getOrElse(parser.decodeFile[AppSettings](new File(s"src/main/resources/application.conf")).toOption.get)).get
  }

}
