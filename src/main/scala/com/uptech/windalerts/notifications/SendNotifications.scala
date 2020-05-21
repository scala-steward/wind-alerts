package com.uptech.windalerts.notifications

import java.io.FileInputStream

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.{Repos, LazyRepos}
import com.uptech.windalerts.alerts.{AlertsService, MongoAlertsRepositoryAlgebra}
import com.uptech.windalerts.domain._
import com.uptech.windalerts.domain.domain.{AlertT, UserT}
import com.uptech.windalerts.status.{BeachService, SwellsService, TidesService, WindsService}
import com.uptech.windalerts.users.{MongoUserRepository, UserRepositoryAlgebra}
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io.{->, /, GET, Ok, _}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}

import scala.concurrent.duration.Duration
import scala.util.Try

object SendNotifications extends IOApp {

  private val logger = getLogger

  logger.error("Starting")
  val started = System.currentTimeMillis()
  sys.addShutdownHook(getLogger.error(s"Shutting down after ${(System.currentTimeMillis() - started)} ms"))

  val dbWithAuthIO = for {
    projectId     <- IO(sys.env("projectId"))
    credentials   <- IO(
      Try(GoogleCredentials.fromStream(new FileInputStream(s"/app/resources/$projectId.json"))).onError(e=>Try(logger.error(e)("Could not load creds from app file")))
                            .orElse(Try(GoogleCredentials.getApplicationDefault)).onError(e=>Try(logger.error(e)("Could not load default creds")))
                            .orElse(Try(GoogleCredentials.fromStream(new FileInputStream(s"src/main/resources/$projectId.json")))).onError(e=>Try(logger.error(e)("Could not load creds from src file")))
                            .getOrElse(GoogleCredentials.getApplicationDefault))
    options       <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId(projectId).build)
    _             <- IO(FirebaseApp.initializeApp(options))
    notifications <- IO(FirebaseMessaging.getInstance)
  } yield (notifications)

  val dbWithAuth = dbWithAuthIO.unsafeRunSync()
  implicit val backend = HttpURLConnectionBackend()

  val conf = secrets.read
  val appConf = config.read
  val key = conf.surfsUp.willyWeather.key
  lazy val beachSeq = beaches.read
  lazy val adjustments = swellAdjustments.read
  val beachesService = new BeachService[IO](new WindsService[IO](key), new TidesService[IO](key), new SwellsService[IO](key, swellAdjustments.read))


  implicit val httpErrorHandler: HttpErrorHandler[IO] = new HttpErrorHandler[IO]


  val repos = new LazyRepos()
  val alerts = new AlertsService[IO](repos)
  val notifications = new Notifications(alerts, beachesService, beachSeq, repos, dbWithAuth, httpErrorHandler, config = config.read)

  def allRoutes() =
    routes().orNotFound


  def routes() = {
    HttpRoutes.of[IO] {
      case GET -> Root / "notify" => {
        val res = notifications.sendNotification
        val either = res.value.unsafeRunSync()
        Ok()
      }
      case GET -> Root => {
        val res = notifications.sendNotification
        val either = res.value.unsafeRunSync()
        Ok()
      }
    }
  }

  def run(args: List[String]): IO[ExitCode] = {

    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withResponseHeaderTimeout(Duration(5, "min"))
      .withIdleTimeout(Duration(8, "min"))
      .withHttpApp(allRoutes())
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }

}