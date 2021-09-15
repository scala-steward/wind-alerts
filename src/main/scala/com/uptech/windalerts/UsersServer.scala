package com.uptech.windalerts

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits._
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.config.{beaches, secrets, swellAdjustments}
import com.uptech.windalerts.core.alerts.AlertsService
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.core.credentials.{AppleCredentials, Credentials, FacebookCredentials, UserCredentialService}
import com.uptech.windalerts.core.otp.{OTPService, OTPWithExpiry}
import com.uptech.windalerts.core.refresh.tokens.RefreshToken
import com.uptech.windalerts.core.social.login.SocialLoginService
import com.uptech.windalerts.core.social.subscriptions.{AndroidToken, AppleToken}
import com.uptech.windalerts.core.user.{AuthenticationService, UserRolesService, UserService, UserT}
import com.uptech.windalerts.infrastructure.{EmailSender, GooglePubSubEventpublisher}
import com.uptech.windalerts.infrastructure.beaches._
import com.uptech.windalerts.infrastructure.endpoints._
import com.uptech.windalerts.infrastructure.endpoints.logger._
import com.uptech.windalerts.infrastructure.repositories.mongo.{MongoAlertsRepository, MongoAndroidPurchaseRepository, MongoApplePurchaseRepository, MongoCredentialsRepository, MongoOtpRepository, MongoRefreshTokenRepository, MongoSocialCredentialsRepository, MongoUserRepository, Repos}
import com.uptech.windalerts.infrastructure.social.subscriptions.{AndroidPublisherHelper, AndroidSubscription, AppleSubscription, ApplicationConfig, SocialPlatformSubscriptionsServiceImpl}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.Logger
import org.log4s.getLogger
object UsersServer extends IOApp {


  implicit val backend = HttpURLConnectionBackend()

  override def run(args: List[String]): IO[ExitCode] =
    Blocker[IO].use { blocker =>
      for {
        _ <- IO(getLogger.info("Starting"))
        projectId = sys.env("projectId")
        googlePublisher = new GooglePubSubEventpublisher[IO](projectId)
        db = Repos.acquireDb
        refreshTokenRepository = new MongoRefreshTokenRepository(db.getCollection[RefreshToken]("refreshTokens"))

        auth <- IO(new AuthenticationService(refreshTokenRepository))

        emailConf = com.uptech.windalerts.config.secrets.read.surfsUp.email
        emailSender = new EmailSender[IO](emailConf.apiKey)

        otpRepositoy = new MongoOtpRepository[IO](db.getCollection[OTPWithExpiry]("otp"))
        usersRepository = new MongoUserRepository(db.getCollection[UserT]("users"))
        credentialsRepository = new MongoCredentialsRepository(db.getCollection[Credentials]("credentials"))
        facebookCredentialsRepository = new MongoSocialCredentialsRepository[IO, FacebookCredentials](db.getCollection[FacebookCredentials]("facebookCredentials"))
        appleCredentialsRepository = new MongoSocialCredentialsRepository[IO, AppleCredentials](db.getCollection[AppleCredentials]("appleCredentials"))
        androidPurchaseRepository = new MongoAndroidPurchaseRepository(db.getCollection[AndroidToken]("androidPurchases"))
        applePurchaseRepository = new MongoApplePurchaseRepository(db.getCollection[AppleToken]("applePurchases"))
        alertsRepository = new MongoAlertsRepository(db.getCollection[Alert]("alerts"))
        androidPublisher = AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL)

        otpService = new OTPService[IO](otpRepositoy, emailSender, usersRepository)
        userCredentialsService <- IO(new UserCredentialService[IO](facebookCredentialsRepository, appleCredentialsRepository, credentialsRepository, usersRepository, refreshTokenRepository, emailSender))
        usersService <- IO(new UserService(usersRepository, userCredentialsService, auth, refreshTokenRepository, googlePublisher))
        socialLoginService <- IO(new SocialLoginService(Repos.applePlatform(), Repos.facebookPlatform(),  facebookCredentialsRepository, appleCredentialsRepository, usersRepository, usersService, userCredentialsService))

        appleSubscription <- IO(new AppleSubscription[IO]())
        androidSubscription <- IO(new AndroidSubscription[IO](androidPublisher))
        subscriptionsService <- IO(new SocialPlatformSubscriptionsServiceImpl[IO](applePurchaseRepository, androidPurchaseRepository, appleSubscription, androidSubscription))
        userRolesService <- IO(new UserRolesService[IO](applePurchaseRepository, androidPurchaseRepository, alertsRepository, usersRepository, otpRepositoy, subscriptionsService, refreshTokenRepository))

        apiKey <- IO(secrets.read.surfsUp.willyWeather.key)
        beachesConfig: Map[Long, beaches.Beach] = com.uptech.windalerts.config.beaches.read

        beaches <- IO(new BeachService[IO](new WWBackedWindsService[IO](apiKey), new WWBackedTidesService[IO](apiKey, beachesConfig), new WWBackedSwellsService[IO](apiKey, swellAdjustments.read)))

        endpoints <- IO(new UsersEndpoints(userCredentialsService, usersService, socialLoginService, userRolesService, subscriptionsService, otpService))

        alertService <- IO(new AlertsService[IO](alertsRepository, usersRepository))
        alertsEndPoints <- IO(new AlertsEndpoints(alertService))

      httpApp <- IO(errors.errorMapper(
        Router(
          "/v1/users" -> auth.middleware(endpoints.authedService()),
          "/v1/users" -> endpoints.openEndpoints(),
          "/v1/users/social/facebook" -> endpoints.facebookEndpoints(),
          "/v1/users/social/apple" -> endpoints.appleEndpoints(),
          "/v1/users/alerts" -> auth.middleware(alertsEndPoints.allUsersService()),
          "/v1/beaches" -> new BeachesEndpoints[IO](beaches).allRoutes(),
          "" -> new SwaggerEndpoints[IO]().endpoints(blocker),

        ).orNotFound))
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