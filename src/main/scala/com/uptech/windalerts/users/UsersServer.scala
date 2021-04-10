package com.uptech.windalerts.users

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits._
import com.http4s.rho.swagger.ui.SwaggerUi
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.LazyRepos
import com.uptech.windalerts.core.alerts.AlertsService
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.core.credentials.UserCredentialService
import com.uptech.windalerts.core.otp.OTPService
import com.uptech.windalerts.core.social.login.SocialLoginService
import com.uptech.windalerts.core.user.{AuthenticationService, UserRolesService, UserService}
import com.uptech.windalerts.domain.{secrets, swellAdjustments}
import com.uptech.windalerts.infrastructure.beaches._
import com.uptech.windalerts.infrastructure.endpoints._
import com.uptech.windalerts.infrastructure.endpoints.logger._
import com.uptech.windalerts.infrastructure.social.subscriptions.SubscriptionsServiceImpl
import org.http4s.implicits._
import org.http4s.rho.swagger.SwaggerMetadata
import org.http4s.rho.swagger.models.{Info, Tag}
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.log4s.getLogger
object UsersServer extends IOApp {


  implicit val backend = HttpURLConnectionBackend()

  override def run(args: List[String]): IO[ExitCode] =
    Blocker[IO].use { blocker =>
      for {
        _ <- IO(getLogger.error("Starting"))
        metadata = SwaggerMetadata(
          apiInfo = Info(title = "Beaches Swagger", version = "v2"),
          basePath = Some("/v2/beaches"),
          tags = List(Tag(name = "Beaches", description = Some("These are the beaches status routes.")))
        )

        swaggerUiRhoMiddleware =
        SwaggerUi[IO].createRhoMiddleware(blocker, swaggerMetadata = metadata)

        repos = new LazyRepos()
        auth <- IO(new AuthenticationService(repos))
        otpService <- IO(new OTPService[IO](repos))
        userCredentialsService <- IO(new UserCredentialService[IO](repos))
        usersService <- IO(new UserService(repos, userCredentialsService, otpService, auth))
        socialLoginService <- IO(new SocialLoginService(repos, usersService))

        subscriptionsService <- IO(new SubscriptionsServiceImpl[IO](repos))
        userRolesService <- IO(new UserRolesService[IO](repos, subscriptionsService, usersService))

        apiKey <- IO(secrets.read.surfsUp.willyWeather.key)
        beaches <- IO(new BeachService[IO](new WWBackedWindsService[IO](apiKey), new WWBackedTidesService[IO](apiKey, repos), new WWBackedSwellsService[IO](apiKey, swellAdjustments.read)))

        endpoints <- IO(new UsersEndpoints(userCredentialsService, usersService, socialLoginService, userRolesService, subscriptionsService))

        alertService <- IO(new AlertsService[IO](usersService, userRolesService, repos))
        alertsEndPoints <- IO(new AlertsEndpoints(alertService))
        beachesEndpointsRho = new BeachesEndpointsRho[IO](beaches).toRoutes(swaggerUiRhoMiddleware)

        httpApp <- IO(errors.errorMapper(Logger.httpApp(true, true, logAction = requestLogger)(
          Router(
            "/v1/users" -> auth.middleware(endpoints.authedService()),
            "/v1/users" -> endpoints.openEndpoints(),
            "/v1/users/social/facebook" -> endpoints.facebookEndpoints(),
            "/v1/users/social/apple" -> endpoints.appleEndpoints(),
            "/v1/users/alerts" -> auth.middleware(alertsEndPoints.allUsersService()),
            "/v2/beaches" -> beachesEndpointsRho,
            "" -> new BeachesEndpoints[IO](beaches).allRoutes(),

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
