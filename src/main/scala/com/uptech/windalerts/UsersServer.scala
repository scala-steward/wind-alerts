package com.uptech.windalerts

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits._
import com.http4s.rho.swagger.ui.SwaggerUi
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.SendNotifications.repos
import com.uptech.windalerts.config.{secrets, swellAdjustments}
import com.uptech.windalerts.core.alerts.AlertsService
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.core.credentials.UserCredentialService
import com.uptech.windalerts.core.otp.{OTPService, OTPWithExpiry}
import com.uptech.windalerts.core.refresh.tokens.RefreshToken
import com.uptech.windalerts.core.social.login.SocialLoginService
import com.uptech.windalerts.core.user.{AuthenticationService, UserRolesService, UserService}
import com.uptech.windalerts.infrastructure.EmailSender
import com.uptech.windalerts.infrastructure.beaches._
import com.uptech.windalerts.infrastructure.endpoints._
import com.uptech.windalerts.infrastructure.endpoints.logger._
import com.uptech.windalerts.infrastructure.repositories.mongo.{LazyRepos, MongoOtpRepository, MongoRefreshTokenRepository, Repos}
import com.uptech.windalerts.infrastructure.social.subscriptions.{AndroidSubscription, AppleSubscription, SubscriptionsServiceImpl}
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
        db = Repos.acquireDb
        refreshTokenRepository = new MongoRefreshTokenRepository(db.getCollection[RefreshToken]("refreshTokens"))

        auth <- IO(new AuthenticationService(refreshTokenRepository))

        emailConf = com.uptech.windalerts.config.secrets.read.surfsUp.email
        emailSender = new EmailSender[IO](emailConf.apiKey)

        otpRepositoy = new MongoOtpRepository[IO](db.getCollection[OTPWithExpiry]("otp"))
        otpService = new OTPService[IO](otpRepositoy, emailSender)

        userCredentialsService <- IO(new UserCredentialService[IO](refreshTokenRepository, emailSender, repos))
        usersService <- IO(new UserService(repos, userCredentialsService, otpService, auth, refreshTokenRepository))
        socialLoginService <- IO(new SocialLoginService(repos, usersService, userCredentialsService))

        appleSubscription <- IO(new AppleSubscription[IO]())
        androidSubscription <- IO(new AndroidSubscription[IO](repos))
        subscriptionsService <- IO(new SubscriptionsServiceImpl[IO](appleSubscription, androidSubscription, repos))
        userRolesService <- IO(new UserRolesService[IO](otpRepositoy, repos, subscriptionsService, usersService))

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
          "/v1/beaches" -> new BeachesEndpoints[IO](beaches).allRoutes(),
          "" -> new SwaggerEndpoints[IO]().endpoints(blocker),

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