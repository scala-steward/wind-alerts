package com.uptech.windalerts.users

import java.io.FileInputStream

import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.FirebaseMessaging
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.alerts.{AlertsEndpoints, AlertsService, MongoAlertsRepositoryAlgebra}
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.logger._
import com.uptech.windalerts.domain.{HttpErrorHandler, config, domain, errors, secrets, swellAdjustments}
import com.uptech.windalerts.notifications.{MongoNotificationsRepository, Notifications, SendNotifications}
import com.uptech.windalerts.notifications.SendNotifications.{alerts, beachSeq, beachesService, coll, db, dbWithAuth, httpErrorHandler, usersRepo}
import com.uptech.windalerts.status.{BeachService, Routes, SwellsService, TidesService, WindsService}
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.log4s.getLogger
import org.mongodb.scala.{MongoClient, MongoCollection}

import scala.util.Try

object UsersServer extends IOApp {
  val started = System.currentTimeMillis()
  sys.addShutdownHook(getLogger.error(s"Shutting down after ${(System.currentTimeMillis() - started)} ms"))
  implicit val backend = HttpURLConnectionBackend()

  override def run(args: List[String]): IO[ExitCode] = for {
    _ <- IO(getLogger.error("Starting"))

    projectId <- IO(sys.env("projectId"))
    credentials <- IO(Try(GoogleCredentials.fromStream(new FileInputStream(s"/app/resources/$projectId.json")))
      .getOrElse(GoogleCredentials.getApplicationDefault))
    applePrivateKey <- IO(Try(AppleLogin.getPrivateKey(s"/app/resources/Apple-$projectId.p8"))
      .getOrElse(AppleLogin.getPrivateKey(s"src/main/resources/Apple.p8")))

    client <- IO.pure(MongoClient(com.uptech.windalerts.domain.secrets.read.surfsUp.mongodb.url))
    mongoDb <- IO(client.getDatabase(sys.env("projectId")).withCodecRegistry(com.uptech.windalerts.domain.codecs.codecRegistry))
    otpColl  <- IO( mongoDb.getCollection[OTPWithExpiry]("otp"))
    otpRepo <- IO( new MongoOtpRepository(otpColl))

    refreshTokensColl  <- IO( mongoDb.getCollection[RefreshToken]("refreshTokens"))
    refreshTokenRepo <- IO( new MongoRefreshTokenRepositoryAlgebra(refreshTokensColl))

    usersColl  <- IO( mongoDb.getCollection[UserT]("users"))
    userRepository <- IO( new MongoUserRepository(usersColl))

    credentialsCollection  <- IO( mongoDb.getCollection[Credentials]("credentials"))
    credentialsRepository <- IO( new MongoCredentialsRepository(credentialsCollection))

    androidPublisher <- IO(AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL))
    androidPurchaseRepoColl  <- IO( mongoDb.getCollection[AndroidToken]("androidPurchases"))

    applePurchaseRepoColl  <- IO( mongoDb.getCollection[AppleToken]("applePurchases"))

    androidPurchaseRepo <- IO( new MongoAndroidPurchaseRepository(androidPurchaseRepoColl))
    applePurchaseRepo <- IO( new MongoApplePurchaseRepository(applePurchaseRepoColl))

    fbcredentialsCollection  <- IO( mongoDb.getCollection[FacebookCredentialsT]("facebookCredentials"))
    fbcredentialsRepository <- IO( new MongoFacebookCredentialsRepository(fbcredentialsCollection))

    appleCredentialsCollection  <- IO( mongoDb.getCollection[AppleCredentials]("appleCredentials"))
    appleCredentialsRepository <- IO( new MongoAppleCredentialsRepositoryAlgebra(appleCredentialsCollection))

    notificationsColl <-  IO(mongoDb.getCollection[Notification]("notifications"))
    notificationsRepository <-  IO(new MongoNotificationsRepository(notificationsColl))

                                                               feedbackColl  <- IO( mongoDb.getCollection[Feedback]("feedbacks"))
    feedbackRepository <- IO( new MongoFeedbackRepository(feedbackColl))

    alertsCollection  <- IO( mongoDb.getCollection[AlertT]("alerts"))
    alertsRepository <- IO( new MongoAlertsRepositoryAlgebra(alertsCollection))
    usersService <- IO(new UserService(userRepository, credentialsRepository, appleCredentialsRepository, fbcredentialsRepository, alertsRepository, feedbackRepository, secrets.read.surfsUp.facebook.key, androidPublisher, applePrivateKey))
    auth <- IO(new Auth(refreshTokenRepo))
    apiKey <- IO(secrets.read.surfsUp.willyWeather.key)
    beaches <- IO(new BeachService[IO](new WindsService[IO](apiKey), new TidesService[IO](apiKey), new SwellsService[IO](apiKey, swellAdjustments.read)))
    httpErrorHandler <- IO(new HttpErrorHandler[IO])

    endpoints <- IO(new UsersEndpoints(usersService, httpErrorHandler, refreshTokenRepo, otpRepo, androidPurchaseRepo, applePurchaseRepo, auth))
    alertService <- IO(new AlertsService[IO](alertsRepository))

    alertsEndPoints <- IO(new AlertsEndpoints(alertService, usersService, auth, httpErrorHandler))
    //notifications <- IO(new Notifications(alerts, beachesService, beachSeq, usersRepo, dbWithAuth._2, httpErrorHandler, notificationsRepository, config = config.read))

    httpApp <- IO(errors.errorMapper(Logger.httpApp(false, true, logAction = requestLogger)(
      Router(
        "/v1/users" -> auth.middleware(endpoints.authedService()),
        "/v1/users" -> endpoints.openEndpoints(),
        "/v1/users/social/facebook" -> endpoints.facebookEndpoints(),
        "/v1/users/social/apple" -> endpoints.appleEndpoints(),

        "/v1/users/alerts" -> auth.middleware(alertsEndPoints.allUsersService()),
        "/v1/notify" ->
          SendNotifications.routes(alertService, beachesService, userRepository, dbWithAuth._2, httpErrorHandler),
        "" -> Routes[IO](beaches, new HttpErrorHandler[IO]).allRoutes(),
      ).orNotFound)))
    server <- BlazeServerBuilder[IO]
                  .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
                  .withHttpApp(httpApp)
                  .serve
                  .compile
                  .drain
                  .as(ExitCode.Success)

  } yield server

}
