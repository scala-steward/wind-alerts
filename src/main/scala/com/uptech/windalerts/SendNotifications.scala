package com.uptech.windalerts

import cats.Parallel
import cats.effect.Resource.eval
import cats.effect._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.softwaremill.sttp.quick.backend
import com.typesafe.config.ConfigFactory.parseFileAnySyntax
import com.uptech.windalerts.config._
import com.uptech.windalerts.config.beaches.{Beaches, _}
import com.uptech.windalerts.config.swellAdjustments.Adjustments
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.core.notifications.{Notification, NotificationsService}
import com.uptech.windalerts.core.refresh.tokens.UserSession
import com.uptech.windalerts.core.user.UserT
import com.uptech.windalerts.infrastructure.beaches.{WWBackedSwellsService, WWBackedTidesService, WWBackedWindsService}
import com.uptech.windalerts.infrastructure.endpoints.NotificationEndpoints
import com.uptech.windalerts.infrastructure.notifications.FirebaseBasedNotificationsSender
import com.uptech.windalerts.infrastructure.repositories.mongo._
import io.circe.config.parser.decodePathF
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Server => H4Server}

import java.io.{File, FileInputStream}
import scala.util.Try

object SendNotifications extends IOApp {
  def createServer[F[_] : ContextShift : ConcurrentEffect : Timer: Parallel](): Resource[F, H4Server[F]] =
    for {
      appConfig <- eval(decodePathF[F, com.uptech.windalerts.config.config.SurfsUp](parseFileAnySyntax(config.getConfigFile("application.conf")), "surfsUp"))
      projectId = sys.env("projectId")

      googleCredentials = firebaseCredentials(config.getSecretsFile(s"firebase/firebase.json"))
      firebaseOptions = new FirebaseOptions.Builder().setCredentials(googleCredentials).setProjectId(projectId).build
      app =  FirebaseApp.initializeApp(firebaseOptions)
      notifications = FirebaseMessaging.getInstance

      beaches <- eval(decodePathF[F, Beaches](parseFileAnySyntax(config.getConfigFile("beaches.json")), "surfsUp"))
      swellAdjustments <- eval(decodePathF[F, Adjustments](parseFileAnySyntax(config.getConfigFile("swellAdjustments.json")), "surfsUp"))
      willyWeatherAPIKey = sys.env("WILLY_WEATHER_KEY")

      beachService = new BeachService[F](
        new WWBackedWindsService[F](willyWeatherAPIKey),
        new WWBackedTidesService[F](willyWeatherAPIKey, beaches.toMap()),
        new WWBackedSwellsService[F](willyWeatherAPIKey, swellAdjustments))

      db = Repos.acquireDb(sys.env("MONGO_DB_URL"))
      usersRepository = new MongoUserRepository[F](db.getCollection[UserT]("users"))
      alertsRepository = new MongoAlertsRepository[F](db.getCollection[DBAlert]("alerts"))
      userSessionsRepository = new MongoUserSessionRepository[F](db.getCollection[DBUserSession]("userSessions"))

      notificationsRepository = new MongoNotificationsRepository[F](db.getCollection[Notification]("notifications"))

      notificationsSender = new FirebaseBasedNotificationsSender[F](notifications, beaches.toMap(), appConfig.notifications )
      notificationService = new NotificationsService[F](notificationsRepository, usersRepository, beachService, alertsRepository, notificationsSender, userSessionsRepository)
      notificationsEndPoints = new NotificationEndpoints[F](notificationService)
      httpApp = notificationsEndPoints.allRoutes()
      server <- BlazeServerBuilder[F]
        .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
        .withHttpApp(httpApp)
        .resource
    } yield server

  private def firebaseCredentials(file:File) = {
    import cats.implicits._

    Try(GoogleCredentials.fromStream(new FileInputStream(file)))
      .onError(e => Try(logger.error("Could not load creds from app file", e)))
      .getOrElse(GoogleCredentials.getApplicationDefault)
  }

  def run(args: List[String]): IO[ExitCode] = createServer.use(_ => IO.never).as(ExitCode.Success)


}