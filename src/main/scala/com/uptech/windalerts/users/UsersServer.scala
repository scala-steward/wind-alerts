package com.uptech.windalerts.users

import cats.effect.{IO, _}
import cats.implicits._
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.LazyRepos
import com.uptech.windalerts.core.alerts.AlertsService
import com.uptech.windalerts.core.beaches.{BeachService, SwellsService, TidesService, WindsService}
import com.uptech.windalerts.core.credentials.UserCredentialService
import com.uptech.windalerts.core.otp.OTPService
import com.uptech.windalerts.core.social.login.SocialLoginService
import com.uptech.windalerts.core.social.subscriptions.SubscriptionsService
import com.uptech.windalerts.core.user.{AuthenticationService, UserRolesService, UserService}
import com.uptech.windalerts.infrastructure.endpoints.logger._
import com.uptech.windalerts.domain.{secrets, swellAdjustments}
import com.uptech.windalerts.infrastructure.endpoints.{AlertsEndpoints, BeachesEndpoints, BeachesEndpointsRho, HttpErrorHandler, HttpErrorHandlerRho, UsersEndpoints, UsersEndpointsRho, errors}
import com.uptech.windalerts.infrastructure.beaches._
import com.uptech.windalerts.infrastructure.social.subscriptions.SubscriptionsServiceImpl
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.log4s.getLogger
import org.http4s.rho.swagger.syntax.{io => ioSwagger}
import cats.effect.{Blocker, ExitCode, IO, IOApp}
import org.http4s.implicits._
import org.http4s.rho.swagger.SwaggerMetadata
import org.http4s.rho.swagger.models.{Info, Tag}
import org.http4s.rho.swagger.syntax.{io => ioSwagger}
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
import com.http4s.rho.swagger.ui.SwaggerUi

import scala.concurrent.ExecutionContext.global
object UsersServer extends IOApp {


  implicit val backend = HttpURLConnectionBackend()

  override def run(args: List[String]): IO[ExitCode] =
    Blocker[IO].use { blocker =>
      for {
        _ <- IO(getLogger.error("Starting"))
        metadataBeaches = SwaggerMetadata(
          apiInfo = Info(title = "Beaches ", version = "v2"),
          basePath = Some("/v2/x"),
          tags = List(Tag(name = "Beaches", description = Some("These are the beach status routes.")))
        )

        swaggerUiRhoMiddlewareBeaches =
        SwaggerUi[IO].createRhoMiddleware(blocker, swaggerMetadata = metadataBeaches)

        metadataUsers = SwaggerMetadata(
          apiInfo = Info(title = "Users ", version = "v2"),
          basePath = Some("/v2/users"),
          tags = List(Tag(name = "Users", description = Some("These are the users status routes.")))
        )

        swaggerUiRhoMiddlewareUsers =
        SwaggerUi[IO].createRhoMiddleware(blocker, swaggerMetadata = metadataUsers)

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

        httpErrorHandler <- IO(new HttpErrorHandler[IO])
        httpErrorHandlerRho <- IO(new HttpErrorHandlerRho[IO])

        endpoints <- IO(new UsersEndpoints(repos, userCredentialsService, usersService, socialLoginService, userRolesService, subscriptionsService, httpErrorHandler))
        endpointsRho <- IO(new UsersEndpointsRho[IO](repos, userCredentialsService, usersService, socialLoginService, userRolesService, subscriptionsService, httpErrorHandler).toRoutes(swaggerUiRhoMiddlewareUsers))

        alertService <- IO(new AlertsService[IO](usersService, userRolesService, repos))
        alertsEndPoints <- IO(new AlertsEndpoints(alertService, usersService, auth, httpErrorHandler))
        myRoutes = new BeachesEndpointsRho[IO](beaches, httpErrorHandlerRho).toRoutes(swaggerUiRhoMiddlewareBeaches)

        httpApp <- IO(errors.errorMapper(Logger.httpApp(true, true, logAction = requestLogger)(
          Router(
            "/v1/users" -> auth.middleware(endpoints.authedService()),
            "/v1/users" -> endpoints.openEndpoints(),
            "/v1/users/social/facebook" -> endpoints.facebookEndpoints(),
            "/v1/users/social/apple" -> endpoints.appleEndpoints(),
            "/v1/users/alerts" -> auth.middleware(alertsEndPoints.allUsersService()),
            "/v2/x" -> myRoutes,
            "/v2/users" -> endpointsRho,
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

}
