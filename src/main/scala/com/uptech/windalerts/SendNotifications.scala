package com.uptech.windalerts

import cats.effect.Resource.eval
import cats.effect._
import cats.mtl.Handle
import cats.{Monad, Parallel}
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.typesafe.config.ConfigFactory.parseFileAnySyntax
import com.uptech.windalerts.config._
import com.uptech.windalerts.config.beaches.{Beaches, _}
import com.uptech.windalerts.core.notifications.NotificationsService
import com.uptech.windalerts.infrastructure.Environment
import com.uptech.windalerts.infrastructure.Environment.{EnvironmentAsk, EnvironmentIOAsk}
import com.uptech.windalerts.infrastructure.beaches.BeachService
import com.uptech.windalerts.infrastructure.endpoints.NotificationEndpoints
import com.uptech.windalerts.infrastructure.notifications.FirebaseBasedNotificationsSender
import com.uptech.windalerts.infrastructure.repositories.mongo._
import io.circe.config.parser.decodePathF
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.{Server => H4Server}
import org.http4s.{Response, Status}

import java.io.{File, FileInputStream}
import scala.util.Try

object SendNotifications extends IOApp {
  implicit val configEnv = new EnvironmentIOAsk(Environment(Repos.acquireDb(sys.env("MONGO_DB_URL"))))

  def createServer[F[_] :EnvironmentAsk: ContextShift : ConcurrentEffect : Timer : Parallel]()(implicit M: Monad[F], H: Handle[F, Throwable]): Resource[F, H4Server] =
    for {
      appConfig <- eval(decodePathF[F, com.uptech.windalerts.config.config.SurfsUp](parseFileAnySyntax(config.getConfigFile("application.conf")), "surfsUp"))
      projectId = sys.env("projectId")

      googleCredentials = firebaseCredentials(config.getSecretsFile(s"firebase/firebase.json"))
      firebaseOptions = new FirebaseOptions.Builder().setCredentials(googleCredentials).setProjectId(projectId).build
      app = FirebaseApp.initializeApp(firebaseOptions)
      notifications = FirebaseMessaging.getInstance

      beaches <- eval(decodePathF[F, Beaches](parseFileAnySyntax(config.getConfigFile("beaches.json")), "surfsUp"))


      beachService <-  BeachService()

      usersRepository = new MongoUserRepository[F]()
      alertsRepository = new MongoAlertsRepository[F]()
      userSessionsRepository = new MongoUserSessionRepository[F]()

      notificationsRepository = new MongoNotificationsRepository[F]()

      notificationsSender = new FirebaseBasedNotificationsSender[F](notifications, beaches.toMap(), appConfig.notifications)
      notificationService = new NotificationsService[F](notificationsRepository, usersRepository, beachService, alertsRepository, notificationsSender, userSessionsRepository)
      notificationsEndPoints = new NotificationEndpoints[F](notificationService)
      httpApp = notificationsEndPoints.allRoutes()
      server <- BlazeServerBuilder[F]
        .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
        .withHttpApp(httpApp)
        .withServiceErrorHandler(_ => {
          case e: Throwable =>
            logger.error("Exception ", e)
            M.pure(Response[F](status = Status.InternalServerError))
        })
        .resource
    } yield server

  private def firebaseCredentials(file: File) = {
    import cats.implicits._

    Try(GoogleCredentials.fromStream(new FileInputStream(file)))
      .onError(e => Try(logger.error("Could not load creds from app file", e)))
      .getOrElse(GoogleCredentials.getApplicationDefault)
  }

  def run(args: List[String]): IO[ExitCode] = {
    createServer[IO].use(_ => IO.never).as(ExitCode.Success)
  }
}