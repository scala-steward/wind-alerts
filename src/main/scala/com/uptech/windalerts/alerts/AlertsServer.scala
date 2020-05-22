package com.uptech.windalerts.alerts

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.uptech.windalerts.LazyRepos
import com.uptech.windalerts.domain.logger.requestLogger
import com.uptech.windalerts.domain.{HttpErrorHandler, errors}
import com.uptech.windalerts.users._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.log4s.getLogger

object AlertsServer extends IOApp {
  val started = System.currentTimeMillis()
  sys.addShutdownHook(getLogger.error(s"Shutting down after ${(System.currentTimeMillis() - started)} ms"))
  override def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- IO(getLogger.error("Starting"))
      projectId <- IO(sys.env("projectId"))
      httpErrorHandler <- IO(new HttpErrorHandler[IO])

      repos <- IO(new LazyRepos())
      alertService <- IO(new AlertsService[IO](repos))
      auth <- IO(new Auth(repos))
      usersService <- IO(  new UserService(new LazyRepos))
      alertsEndPoints <- IO(new AlertsEndpoints(alertService, usersService, auth, httpErrorHandler))
      httpApp <- IO(Logger.httpApp(false, true, logAction = requestLogger)(errors.errorMapper(Router(
        "/v1/users/alerts" -> auth.middleware(alertsEndPoints.allUsersService())
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
}
