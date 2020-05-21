package com.uptech.windalerts.users

import cats.effect.{IO, _}
import cats.implicits._
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.LazyRepos
import com.uptech.windalerts.alerts.{AlertsEndpoints, AlertsService, MongoAlertsRepositoryAlgebra}
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.logger._
import com.uptech.windalerts.domain.{HttpErrorHandler, errors, secrets, swellAdjustments}
import com.uptech.windalerts.status._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.log4s.getLogger
import org.mongodb.scala.MongoClient

import scala.util.Try

object UsersServer extends IOApp {

  implicit val backend = HttpURLConnectionBackend()

  override def run(args: List[String]): IO[ExitCode] = for {
    _ <- IO(getLogger.error("Starting"))

    projectId <- IO(sys.env("projectId"))
    applePrivateKey <- IO(Try(AppleLogin.getPrivateKey(s"/app/resources/Apple-$projectId.p8"))
      .getOrElse(AppleLogin.getPrivateKey(s"src/main/resources/Apple.p8")))




    emailConf = com.uptech.windalerts.domain.secrets.read.surfsUp.email
    emailSender = new EmailSender(emailConf.userName, emailConf.password)
    repos = new LazyRepos()
    usersService <- IO(new UserService(repos,secrets.read.surfsUp.facebook.key, applePrivateKey, emailSender))
    auth <- IO(new Auth(repos))
    apiKey <- IO(secrets.read.surfsUp.willyWeather.key)

    beaches <- IO(new BeachService[IO](new WindsService[IO](apiKey), new TidesService[IO](apiKey), new SwellsService[IO](apiKey, swellAdjustments.read)))
    httpErrorHandler <- IO(new HttpErrorHandler[IO])

    endpoints <- IO(new UsersEndpoints(repos, usersService, httpErrorHandler, auth))

    alertService <- IO(new AlertsService[IO](repos))
    alertsEndPoints <- IO(new AlertsEndpoints(alertService, usersService, auth, httpErrorHandler))

    httpApp <- IO(errors.errorMapper(Logger.httpApp(false, true, logAction = requestLogger)(
      Router(
        "/v1/users" -> auth.middleware(endpoints.authedService()),
        "/v1/users" -> endpoints.openEndpoints(),
        "/v1/users/social/facebook" -> endpoints.facebookEndpoints(),
        "/v1/users/social/apple" -> endpoints.appleEndpoints(),
        "/v1/users/alerts" -> auth.middleware(alertsEndPoints.allUsersService()),
        "" -> Routes[IO](beaches, httpErrorHandler).allRoutes(),
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
