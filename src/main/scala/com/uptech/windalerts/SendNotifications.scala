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
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.core.credentials.{AppleCredentials, Credentials, FacebookCredentials, UserCredentialService}
import com.uptech.windalerts.core.notifications.{Notification, NotificationsService}
import com.uptech.windalerts.core.otp.{OTPService, OTPWithExpiry}
import com.uptech.windalerts.core.refresh.tokens.RefreshToken
import com.uptech.windalerts.core.social.subscriptions.{AndroidToken, AppleToken}
import com.uptech.windalerts.core.user.{AuthenticationService, UserRolesService, UserService, UserT}
import com.uptech.windalerts.infrastructure.EmailSender
import com.uptech.windalerts.infrastructure.beaches.{WWBackedSwellsService, WWBackedTidesService, WWBackedWindsService}
import com.uptech.windalerts.infrastructure.endpoints.NotificationEndpoints
import com.uptech.windalerts.infrastructure.notifications.FirebaseBasedNotificationsSender
import com.uptech.windalerts.infrastructure.repositories.mongo.{ MongoAlertsRepository, MongoAndroidPurchaseRepository, MongoApplePurchaseRepository, MongoCredentialsRepository, MongoNotificationsRepository, MongoOtpRepository, MongoRefreshTokenRepository, MongoSocialCredentialsRepository, MongoUserRepository, Repos}
import com.uptech.windalerts.infrastructure.social.subscriptions.{AppleSubscription, SocialPlatformSubscriptionsServiceImpl}
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
  val beachesConfig: Map[Long, beaches.Beach] = com.uptech.windalerts.config.beaches.read
  val beachesService = new BeachService[IO](new WWBackedWindsService[IO](key), new WWBackedTidesService[IO](key, beachesConfig), new WWBackedSwellsService[IO](key, swellAdjustments.read))
  val db = Repos.acquireDb
  val emailConf = com.uptech.windalerts.config.secrets.read.surfsUp.email
  val emailSender = new EmailSender[IO](emailConf.apiKey)
  val otpRepository = new MongoOtpRepository[IO](db.getCollection[OTPWithExpiry]("otp"))
  val refreshTokenRepository = new MongoRefreshTokenRepository(db.getCollection[RefreshToken]("refreshTokens"))
  val otpService = new OTPService[IO](otpRepository, emailSender)

  val auth = new AuthenticationService(refreshTokenRepository)
  val usersRepository = new MongoUserRepository(db.getCollection[UserT]("users"))
  val credentialsRepository = new MongoCredentialsRepository(db.getCollection[Credentials]("credentials"))
  val facebookCredentialsRepository = new MongoSocialCredentialsRepository[IO, FacebookCredentials](db.getCollection[FacebookCredentials]("facebookCredentials"))
  val appleCredentialsRepository = new MongoSocialCredentialsRepository[IO, AppleCredentials](db.getCollection[AppleCredentials]("appleCredentials"))
  val androidPurchaseRepository = new MongoAndroidPurchaseRepository(db.getCollection[AndroidToken]("androidPurchases"))
  val applePurchaseRepository = new MongoApplePurchaseRepository(db.getCollection[AppleToken]("applePurchases"))
  val alertsRepository = new MongoAlertsRepository(db.getCollection[Alert]("alerts"))
  val notificationsRepository = new MongoNotificationsRepository(db.getCollection[Notification]("notifications"))
  val userCredentialsService = new UserCredentialService[IO](facebookCredentialsRepository, appleCredentialsRepository, credentialsRepository, usersRepository, refreshTokenRepository, emailSender)
  val usersService = new UserService(usersRepository, userCredentialsService, otpService, auth, refreshTokenRepository)
  val appleSubscription = new AppleSubscription[IO]
  val androidSubscription = new AppleSubscription[IO]

  val subscriptionService = new SocialPlatformSubscriptionsServiceImpl(applePurchaseRepository, androidPurchaseRepository, appleSubscription, androidSubscription)

  val userRolesService = new UserRolesService(applePurchaseRepository, androidPurchaseRepository, alertsRepository, usersRepository, otpRepository, subscriptionService, usersService)

  val alerts = new AlertsService[IO](alertsRepository, usersService, userRolesService)
  val notificationsSender = new FirebaseBasedNotificationsSender[IO](firebaseMessaging, beachSeq, appConf )
  val notifications = new NotificationsService(notificationsRepository, usersRepository, alerts, beachesService, notificationsSender)
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