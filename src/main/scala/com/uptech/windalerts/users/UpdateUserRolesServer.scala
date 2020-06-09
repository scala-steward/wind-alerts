package com.uptech.windalerts.users

import cats.effect.{IO, _}
import cats.implicits._
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.LazyRepos
import com.uptech.windalerts.alerts.{AlertsEndpoints, AlertsService}
import com.uptech.windalerts.domain.logger._
import com.uptech.windalerts.domain.{HttpErrorHandler, errors, secrets, swellAdjustments}
import com.uptech.windalerts.status._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.log4s.getLogger

object UpdateUserRolesServer extends IOApp {

  implicit val backend = HttpURLConnectionBackend()

  override def run(args: List[String]): IO[ExitCode] = for {
    _ <- IO(getLogger.error("Starting"))

    repos = new LazyRepos()
    authService <- IO(new AuthenticationServiceImpl(repos))
    otpService <-  IO(new OTPService[IO](repos, authService))
    httpErrorHandler <- IO(new HttpErrorHandler[IO])
    subscriptionsService <- IO(new SubscriptionsService[IO](repos))
    endpoints <- IO(new UpdateUserRolesEndpoints[IO](new UserRolesService[IO](repos, subscriptionsService), httpErrorHandler))


    httpApp <- IO(errors.errorMapper(Logger.httpApp(true, true, logAction = requestLogger)(
      Router(
        "/v1/users/roles" -> endpoints.endpoints(),
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
