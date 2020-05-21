package com.uptech.windalerts.alerts

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.uptech.windalerts.LazyRepos
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.logger.requestLogger
import com.uptech.windalerts.domain.{HttpErrorHandler, errors, secrets}
import com.uptech.windalerts.users._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.log4s.getLogger
import org.mongodb.scala.MongoClient

import scala.util.Try

object AlertsServer extends IOApp {
  val started = System.currentTimeMillis()
  sys.addShutdownHook(getLogger.error(s"Shutting down after ${(System.currentTimeMillis() - started)} ms"))
  override def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- IO(getLogger.error("Starting"))
      projectId <- IO(sys.env("projectId"))
      httpErrorHandler <- IO(new HttpErrorHandler[IO])
      applePrivateKey <- IO(Try(AppleLogin.getPrivateKey(s"/app/resources/Apple-$projectId.p8"))
        .getOrElse(AppleLogin.getPrivateKey(s"src/main/resources/Apple.p8")))

      repos <- IO(new LazyRepos())
      alertService <- IO(new AlertsService[IO](repos))
      auth <- IO(new Auth(repos))
      emailConf = com.uptech.windalerts.domain.secrets.read.surfsUp.email
      emailSender = new EmailSender(emailConf.userName, emailConf.password)
      usersService <- IO(  new UserService(new LazyRepos, secrets.read.surfsUp.facebook.key, applePrivateKey, emailSender))
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
