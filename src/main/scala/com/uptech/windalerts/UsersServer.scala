package com.uptech.windalerts

import cats.effect.Resource.eval
import cats.effect.{IO, _}
import cats.{Monad, Parallel}
import com.softwaremill.sttp.quick.backend
import com.typesafe.config.ConfigFactory.parseFileAnySyntax
import com.uptech.windalerts.config._
import com.uptech.windalerts.config.beaches.{Beaches, _}
import com.uptech.windalerts.config.swellAdjustments.Adjustments
import com.uptech.windalerts.core.alerts.AlertsService
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.core.credentials.{Credentials, SocialCredentials, UserCredentialService}
import com.uptech.windalerts.core.otp.{OTPService, OTPWithExpiry}
import com.uptech.windalerts.core.social.login.SocialLoginService
import com.uptech.windalerts.core.social.subscriptions.PurchaseToken
import com.uptech.windalerts.core.user.{AuthenticationService, UserRolesService, UserService}
import com.uptech.windalerts.infrastructure.beaches.{WWBackedSwellsService, WWBackedTidesService, WWBackedWindsService}
import com.uptech.windalerts.infrastructure.endpoints._
import com.uptech.windalerts.infrastructure.repositories.mongo._
import com.uptech.windalerts.infrastructure.social.SocialPlatformTypes.{Apple, Facebook}
import com.uptech.windalerts.infrastructure.social.login.{AppleLoginProvider, FacebookLoginProvider, FixedSocialLoginProviders}
import com.uptech.windalerts.infrastructure.social.subscriptions._
import com.uptech.windalerts.infrastructure.{SendInBlueEmailSender, GooglePubSubEventpublisher}
import io.circe.config.parser.decodePathF
import org.http4s.{Response, Status}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Router, Server => H4Server}

object UsersServer extends IOApp {
  def createServer[F[_] : ContextShift : ConcurrentEffect : Timer : Parallel]()(implicit M:Monad[F]): Resource[F, H4Server[F]] =

    for {
      beaches <- eval(decodePathF[F, Beaches](parseFileAnySyntax(config.getConfigFile("beaches.json")), "surfsUp"))
      swellAdjustments <- eval(decodePathF[F, Adjustments](parseFileAnySyntax(config.getConfigFile("swellAdjustments.json")), "surfsUp"))
      willyWeatherAPIKey =  sys.env("WILLY_WEATHER_KEY")


      projectId = sys.env("projectId")

      googlePublisher = new GooglePubSubEventpublisher[F](projectId)
      androidPublisher = AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL)

      db = Repos.acquireDb(sys.env("MONGO_DB_URL"))
      userSessionsRepository = new MongoUserSessionRepository[F](db.getCollection[DBUserSession]("userSessions"))
      otpRepositoy = new MongoOtpRepository[F](db.getCollection[DBOTPWithExpiry]("otp"))
      usersRepository = new MongoUserRepository[F](db.getCollection[DBUser]("users"))
      credentialsRepository = new MongoCredentialsRepository[F](db.getCollection[DBCredentials]("credentials"))
      facebookCredentialsRepository = new MongoSocialCredentialsRepository[F](db.getCollection[DBSocialCredentials]("facebookCredentials"))
      appleCredentialsRepository = new MongoSocialCredentialsRepository[F](db.getCollection[DBSocialCredentials]("appleCredentials"))
      androidPurchaseRepository = new MongoPurchaseTokenRepository[F](db.getCollection[DBPurchaseToken]("androidPurchases"))
      applePurchaseRepository = new MongoPurchaseTokenRepository[F](db.getCollection[DBPurchaseToken]("applePurchases"))

      alertsRepository = new MongoAlertsRepository[F](db.getCollection[DBAlert]("alerts"))
      applePlatform = new AppleLoginProvider[F](config.getSecretsFile(s"apple/Apple.p8"))
      facebookPlatform = new FacebookLoginProvider[F](sys.env("FACEBOOK_KEY"))
      beachService = new BeachService[F](
        new WWBackedWindsService[F](willyWeatherAPIKey),
        new WWBackedTidesService[F](willyWeatherAPIKey, beaches.toMap()),
        new WWBackedSwellsService[F](willyWeatherAPIKey, swellAdjustments))
      auth = new AuthenticationService[F](sys.env("JWT_KEY"), usersRepository)
      emailSender = new SendInBlueEmailSender[F](sys.env("EMAIL_KEY"))
      otpService = new OTPService(otpRepositoy, emailSender)
      socialCredentialsRepositories = Map(Facebook -> facebookCredentialsRepository, Apple -> appleCredentialsRepository)

      userCredentialsService = new UserCredentialService[F](socialCredentialsRepositories, credentialsRepository, usersRepository, userSessionsRepository, emailSender)
      usersService = new UserService[F](usersRepository, userCredentialsService, auth, userSessionsRepository, googlePublisher)
      socialLoginPlatforms = new FixedSocialLoginProviders[F](applePlatform, facebookPlatform)
      socialLoginService = new SocialLoginService[F](usersRepository, usersService, userCredentialsService, socialCredentialsRepositories, socialLoginPlatforms)

      appleSubscription = new AppleSubscription[F](sys.env("APPLE_APP_SECRET"))
      androidSubscription = new AndroidSubscription[F](androidPublisher)
      subscriptionsService = new AllSocialPlatformSubscriptionsProviders[F](applePurchaseRepository, androidPurchaseRepository, appleSubscription, androidSubscription)
      userRolesService = new UserRolesService[F](alertsRepository, usersRepository, otpRepositoy, subscriptionsService)
      socialPlatformSubscriptionsProviders = new AllSocialPlatformSubscriptionsProviders[F](applePurchaseRepository, androidPurchaseRepository, appleSubscription, androidSubscription)
      endpoints = new UsersEndpoints[F](userCredentialsService, usersService, socialLoginService, userRolesService, socialPlatformSubscriptionsProviders, otpService)
      alertService = new AlertsService[F](alertsRepository)
      alertsEndPoints = new AlertsEndpoints[F](alertService)
      blocker <- Blocker[F]
      httpApp = Router(
        "/v1/users" -> auth.middleware(endpoints.authedService()),
        "/v1/users" -> endpoints.openEndpoints(),
        "/v1/users/social/facebook" -> endpoints.facebookEndpoints(),
        "/v1/users/social/apple" -> endpoints.appleEndpoints(),
        "/v1/users/alerts" -> auth.middleware(alertsEndPoints.allUsersService()),
        "/v1/beaches" -> new BeachesEndpoints[F](beachService).allRoutes(),
        "" -> new SwaggerEndpoints[F]().endpoints(blocker),
      ).orNotFound
      server <- BlazeServerBuilder[F]
        .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
        .withHttpApp(new errors[F].errorMapper(httpApp))
        .withServiceErrorHandler(_ => {
          case e: Throwable =>
            logger.error("Exception ", e)
            M.pure(Response[F](status = Status.InternalServerError))
        })
        .resource
    } yield server

  def run(args: List[String]): IO[ExitCode] = createServer.use(_ => IO.never).as(ExitCode.Success)

}
