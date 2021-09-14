package com.uptech.windalerts

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.config._
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.core.notifications.{Notification, NotificationsService}
import com.uptech.windalerts.core.user.UserT
import com.uptech.windalerts.infrastructure.beaches.{WWBackedSwellsService, WWBackedTidesService, WWBackedWindsService}
import com.uptech.windalerts.infrastructure.endpoints.NotificationEndpoints
import com.uptech.windalerts.infrastructure.notifications.FirebaseBasedNotificationsSender
import com.uptech.windalerts.infrastructure.repositories.mongo.{MongoAlertsRepository, MongoNotificationsRepository, MongoUserRepository, Repos}
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger

import java.io.FileInputStream
import scala.concurrent.duration.Duration
import scala.util.Try

object SendNotifications extends IOApp {

  private val logger = getLogger
  logger.error("Starting")
  val started = System.currentTimeMillis()
  sys.addShutdownHook(getLogger.error(s"Shutting down after ${(System.currentTimeMillis() - started)} ms"))

  val projectId = sys.env("projectId")

  val firebaseMessaging = (for {
    credentials <- IO(
      Try(GoogleCredentials.fromStream(new FileInputStream(s"/app/resources/$projectId.json"))).onError(e => Try(logger.error(e)("Could not load creds from app file")))
        .orElse(Try(GoogleCredentials.getApplicationDefault)).onError(e => Try(logger.error(e)("Could not load default creds")))
        .orElse(Try(GoogleCredentials.fromStream(new FileInputStream(s"src/main/resources/$projectId.json")))).onError(e => Try(logger.error(e)("Could not load creds from src file")))
        .getOrElse(GoogleCredentials.getApplicationDefault))
    options <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId(projectId).build)
    _ <- IO(FirebaseApp.initializeApp(options))
    notifications <- IO(FirebaseMessaging.getInstance)
  } yield (notifications)).unsafeRunSync()

  implicit val backend = HttpURLConnectionBackend()

  val conf = secrets.read
  val appConf = config.read
  val key = conf.surfsUp.willyWeather.key
  lazy val beachSeq = beaches.read
  lazy val adjustments = swellAdjustments.read
  val beachesConfig: Map[Long, beaches.Beach] = com.uptech.windalerts.config.beaches.read
  val beachesService = new BeachService[IO](new WWBackedWindsService[IO](key), new WWBackedTidesService[IO](key, beachesConfig), new WWBackedSwellsService[IO](key, swellAdjustments.read))
  val db = Repos.acquireDb

  val usersRepository = new MongoUserRepository(db.getCollection[UserT]("users"))
  val alertsRepository = new MongoAlertsRepository(db.getCollection[Alert]("alerts"))
  val notificationsRepository = new MongoNotificationsRepository(db.getCollection[Notification]("notifications"))

  val notificationsSender = new FirebaseBasedNotificationsSender[IO](firebaseMessaging, beachSeq, appConf )
  val notifications = new NotificationsService[IO](notificationsRepository, usersRepository, beachesService, alertsRepository, notificationsSender)
  val notificationsEndPoints = new NotificationEndpoints[IO](notifications)


  def run(args: List[String]): IO[ExitCode] = {

    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withResponseHeaderTimeout(Duration(5, "min"))
      .withIdleTimeout(Duration(8, "min"))
      .withHttpApp(notificationsEndPoints.allRoutes())
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }

}