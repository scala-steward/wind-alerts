package com.uptech.windalerts.users

import cats.effect.{IO, _}
import cats.implicits._
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.LazyRepos
import com.uptech.windalerts.alerts.AlertsService
import com.uptech.windalerts.domain.logger._
import com.uptech.windalerts.domain.{HttpErrorHandler, errors, secrets, swellAdjustments}
import com.uptech.windalerts.infrastructure.endpoints.{AlertsEndpoints, BeachesEndpoints, UsersEndpoints}
import com.uptech.windalerts.social.login.{SocialLoginService}
import com.uptech.windalerts.status._
import com.uptech.windalerts.social.subcriptions.SubscriptionsService
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.log4s.getLogger

object UsersServer extends IOApp {


  implicit val backend = HttpURLConnectionBackend()

  override def run(args: List[String]): IO[ExitCode] = for {
    _ <- IO(getLogger.error("Starting"))

    repos = new LazyRepos()
    auth <- IO(new AuthenticationServiceImpl(repos))
    otpService <- IO(new OTPService[IO](repos, auth))
    userCredentialsService <- IO(new UserCredentialService[IO](repos))
    usersService <- IO(new UserService(repos, userCredentialsService, otpService, auth))
    socialLoginService <- IO(new SocialLoginService(repos, usersService))

    subscriptionsService <- IO(new SubscriptionsService[IO](repos))
    userRolesService <- IO(new UserRolesService[IO](repos, subscriptionsService))

    apiKey <- IO(secrets.read.surfsUp.willyWeather.key)
    beaches <- IO(new BeachService[IO](new WindsService[IO](apiKey), new TidesService[IO](apiKey, repos), new SwellsService[IO](apiKey, swellAdjustments.read)))
    httpErrorHandler <- IO(new HttpErrorHandler[IO])

    endpoints <- IO(new UsersEndpoints(repos, userCredentialsService, usersService, socialLoginService, userRolesService, subscriptionsService, httpErrorHandler))

    alertService <- IO(new AlertsService[IO](repos))
    alertsEndPoints <- IO(new AlertsEndpoints(alertService, usersService, auth, httpErrorHandler))

    httpApp <- IO(errors.errorMapper(Logger.httpApp(true, true, logAction = requestLogger)(
      Router(
        "/v1/users" -> auth.middleware(endpoints.authedService()),
        "/v1/users" -> endpoints.openEndpoints(),
        "/v1/users/social/facebook" -> endpoints.facebookEndpoints(),
        "/v1/users/social/apple" -> endpoints.appleEndpoints(),
        "/v1/users/alerts" -> auth.middleware(alertsEndPoints.allUsersService()),
        "" -> new BeachesEndpoints[IO](beaches, httpErrorHandler).allRoutes(),
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
