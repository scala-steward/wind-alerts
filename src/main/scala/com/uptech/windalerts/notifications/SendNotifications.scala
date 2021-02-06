package com.uptech.windalerts.notifications

import java.io.FileInputStream
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.LazyRepos
import com.uptech.windalerts.core.AlertsService
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.domain._
import com.uptech.windalerts.infrastructure.beaches.{WillyWeatherBackedSwellService, WillyWeatherBackedTidesService, WillyWeatherBackedWindsService}
import com.uptech.windalerts.infrastructure.endpoints.NotificationEndpoints
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger

import scala.concurrent.duration.Duration
import scala.util.Try

object SendNotifications extends IOApp {

  private val logger = getLogger

  logger.error("Starting")
  val started = System.currentTimeMillis()
  sys.addShutdownHook(getLogger.error(s"Shutting down after ${(System.currentTimeMillis() - started)} ms"))
  val repos = new LazyRepos()

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
  val beachesService = new BeachService[IO](new WillyWeatherBackedWindsService[IO](key), new WillyWeatherBackedTidesService[IO](key, repos), new WillyWeatherBackedSwellService[IO](key, swellAdjustments.read))


  implicit val httpErrorHandler: HttpErrorHandler[IO] = new HttpErrorHandler[IO]


  val alerts = new AlertsService[IO](repos)
  val notifications = new Notifications(alerts, beachesService, beachSeq, repos, dbWithAuth, httpErrorHandler, config = config.read)
  val notificationsEndPoints = new NotificationEndpoints[IO](notifications, httpErrorHandler)


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