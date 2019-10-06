package com.uptech.windalerts.alerts

import java.io.FileInputStream

import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.domain.HttpErrorHandler
import com.uptech.windalerts.status.{Beaches, Swells, Tides, Winds}
import com.uptech.windalerts.users.{Devices, Users, UsersRepository}
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
import com.uptech.windalerts.domain.codecs._

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
    auth <- IO(FirebaseAuth.getInstance)
    notifications <- IO(FirebaseMessaging.getInstance)
  } yield (db, auth, notifications)

  val dbWithAuth = dbWithAuthIO.unsafeRunSync()

  val beaches = Beaches.ServiceImpl(Winds.impl, Swells.impl, Tides.impl)
  val alertsRepo:AlertsRepository.Repository = new AlertsRepository.FirebaseBackedRepository(dbWithAuth._1)

  val alerts = new AlertsService.ServiceImpl(alertsRepo)
  val users = new Users.FireStoreBackedService(dbWithAuth._2)
  val usersRepo =  new UsersRepository.FirestoreBackedRepository(dbWithAuth._1)

  val devices = new Devices.FireStoreBackedService(dbWithAuth._1)
  implicit val httpErrorHandler: HttpErrorHandler[IO] = new HttpErrorHandler[IO]
  val notifications = new Notifications(alerts, beaches, users, devices, usersRepo, dbWithAuth._3, httpErrorHandler)

  def allRoutes(A: AlertsService.Service, B: Beaches.Service, UR:UsersRepository.Repository, firebaseMessaging: FirebaseMessaging, H:HttpErrorHandler[IO]) = HttpRoutes.of[IO] {
    case GET -> Root / "notify" => {
      val res = notifications.sendNotification
      val either = res.attempt.unsafeRunSync()
      either.fold(H.handleThrowable, _ => Ok(either.right.get))
    }
  }.orNotFound


  def run(args: List[String]): IO[ExitCode] = {

    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withHttpApp(allRoutes(alerts, beaches,  usersRepo, dbWithAuth._3, httpErrorHandler))
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }

}
