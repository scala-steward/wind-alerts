package com.uptech.windalerts

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.SendNotifications.firebaseMessaging
import com.uptech.windalerts.config._
import com.uptech.windalerts.core.alerts.AlertsService
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.core.credentials.UserCredentialService
import com.uptech.windalerts.core.notifications.NotificationsService
import com.uptech.windalerts.core.otp.{OTPService, OTPWithExpiry}
import com.uptech.windalerts.core.user.{AuthenticationService, UserRolesService, UserService}
import com.uptech.windalerts.infrastructure.beaches.{WWBackedSwellsService, WWBackedTidesService, WWBackedWindsService}
import com.uptech.windalerts.infrastructure.endpoints.NotificationEndpoints
import com.uptech.windalerts.infrastructure.notifications.FirebaseBasedNotificationsSender
import com.uptech.windalerts.infrastructure.repositories.mongo.{LazyRepos, MongoOtpRepository, Repos}
import com.uptech.windalerts.infrastructure.social.subscriptions.{AppleSubscription, SubscriptionsServiceImpl}
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
  val repos = new LazyRepos()

  val firebaseMessaging = (for {
    projectId <- IO(sys.env("projectId"))
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
  val beachesService = new BeachService[IO](new WWBackedWindsService[IO](key), new WWBackedTidesService[IO](key, repos), new WWBackedSwellsService[IO](key, swellAdjustments.read))
  val db = Repos.acquireDb
  val otpRepositoy = new MongoOtpRepository[IO](db.getCollection[OTPWithExpiry]("otp"))
  val otpService = new OTPService[IO](otpRepositoy, repos)

  val auth = new AuthenticationService(repos)

  val userCredentialsService = new UserCredentialService[IO](repos)
  val usersService = new UserService(repos, userCredentialsService, otpService, auth)
  val appleSubscription = new AppleSubscription[IO]
  val androidSubscription = new AppleSubscription[IO]

  val subscriptionService = new SubscriptionsServiceImpl(appleSubscription, androidSubscription, repos)

  val userRolesService = new UserRolesService(otpRepositoy, repos, subscriptionService, usersService)

  val alerts = new AlertsService[IO](usersService, userRolesService, repos)
  val notificationsSender = new FirebaseBasedNotificationsSender[IO](firebaseMessaging, beachSeq, appConf )
  val notifications = new NotificationsService(alerts, beachesService, repos, notificationsSender)
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