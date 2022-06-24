package com.uptech.windalerts

import cats.effect.Resource.eval
import cats.effect.{IO, _}
import cats.mtl.Handle
import cats.{Monad, Parallel}
import com.softwaremill.sttp.quick.backend
import com.typesafe.config.ConfigFactory.parseFileAnySyntax
import com.uptech.windalerts.config._
import com.uptech.windalerts.config.beaches.{Beaches, _}
import com.uptech.windalerts.config.swellAdjustments.Adjustments
import com.uptech.windalerts.core.alerts.AlertsService
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.core.otp.OTPService
import com.uptech.windalerts.core.social.login.SocialLoginService
import com.uptech.windalerts.core.user.credentials.UserCredentialService
import com.uptech.windalerts.core.user.sessions.UserSessions
import com.uptech.windalerts.core.user.{UserRolesService, UserService}
import com.uptech.windalerts.infrastructure.Environment.{EnvironmentAsk, EnvironmentIOAsk}
import com.uptech.windalerts.infrastructure.beaches.{WWBackedSwellsService, WWBackedTidesService, WWBackedWindsService}
import com.uptech.windalerts.infrastructure.endpoints._
import com.uptech.windalerts.infrastructure.repositories.mongo._
import com.uptech.windalerts.infrastructure.social.SocialPlatformTypes.{Apple, Facebook}
import com.uptech.windalerts.infrastructure.social.login.{AllSocialLoginProviders, AppleLoginProvider, FacebookLoginProvider}
import com.uptech.windalerts.infrastructure.social.subscriptions._
import com.uptech.windalerts.infrastructure.{Environment, GooglePubSubEventpublisher, SendInBlueEmailSender}
import io.circe.config.parser.decodePathF
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._
import org.http4s.server.{Router, Server => H4Server}
import org.http4s.{Response, Status}

object UsersServer extends IOApp {
  implicit val configEnv = new EnvironmentIOAsk(Environment(Repos.acquireDb(sys.env("MONGO_DB_URL"))))

  def createServer[F[_] : EnvironmentAsk : ContextShift : ConcurrentEffect : Timer : Parallel]()(implicit M: Monad[F], H: Handle[F, Throwable]): Resource[F, H4Server] = {
    val projectId = sys.env("projectId")
    val googlePublisher = new GooglePubSubEventpublisher[F](projectId)
    val androidPublisher = AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL)


    val otpRepository = new MongoOtpRepository[F]()
    val usersRepository = new MongoUserRepository[F]()
    val androidPurchaseRepository = new MongoPurchaseTokenRepository[F]("androidPurchases")
    val applePurchaseRepository = new MongoPurchaseTokenRepository[F]("applePurchases")

    val alertsRepository = new MongoAlertsRepository[F]()
    val auth = new AuthenticationMiddleware[F](sys.env("JWT_KEY"), usersRepository)
    val emailSender = new SendInBlueEmailSender[F](sys.env("EMAIL_KEY"))
    val otpService = new OTPService(otpRepository, emailSender)
    val socialCredentialsRepositories = Map(
      Facebook -> new MongoSocialCredentialsRepository[F]("facebookCredentials"),
      Apple -> new MongoSocialCredentialsRepository[F]("appleCredentials"))

    val userCredentialsService = new UserCredentialService[F](socialCredentialsRepositories, new MongoCredentialsRepository[F]())
    val userSessions = new UserSessions[F](sys.env("JWT_KEY"), new MongoUserSessionRepository[F]())
    val usersService = new UserService[F](usersRepository, userCredentialsService, userSessions, googlePublisher, emailSender)
    val socialLoginPlatforms = new AllSocialLoginProviders[F](
      new AppleLoginProvider[F](config.getSecretsFile(s"apple/Apple.p8")),
      new FacebookLoginProvider[F](sys.env("FACEBOOK_KEY")))
    val socialLoginService = new SocialLoginService[F](usersRepository, userSessions, userCredentialsService, socialCredentialsRepositories, socialLoginPlatforms)

    val appleSubscription = new AppleSubscription[F](sys.env("APPLE_APP_SECRET"))
    val androidSubscription = new AndroidSubscription[F](androidPublisher)
    val subscriptionsService = new AllSocialPlatformSubscriptionsProviders[F](applePurchaseRepository, androidPurchaseRepository, appleSubscription, androidSubscription)
    val userRolesService = new UserRolesService[F](alertsRepository, usersRepository, otpRepository, subscriptionsService)
    val socialPlatformSubscriptionsProviders = new AllSocialPlatformSubscriptionsProviders[F](applePurchaseRepository, androidPurchaseRepository, appleSubscription, androidSubscription)
    val endpoints = new UsersEndpoints[F](userCredentialsService, userSessions, usersService, socialLoginService, userRolesService, socialPlatformSubscriptionsProviders, otpService)
    val alertService = new AlertsService[F](alertsRepository)
    val alertsEndPoints = new AlertsEndpoints[F](alertService)
    for {
      beachService <- com.uptech.windalerts.infrastructure.beaches.BeachService[F]()
      blocker <- Blocker[F]
      httpApp = Router(
        "/v1/users" -> auth.middleware(endpoints.authedService()),
        "/v1/users" -> endpoints.openEndpoints(),
        "/v1/users/social/facebook" -> endpoints.facebookEndpoints(),
        "/v1/users/social/apple" -> endpoints.appleEndpoints(),
        "/v1/users/alerts" -> auth.middleware(alertsEndPoints.allUsersService()),
        "/v1/beaches" -> new BeachesEndpoints[F](beachService).allRoutes,
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
  }


  def run(args: List[String]): IO[ExitCode] = {
    createServer[IO].use(_ => IO.never).as(ExitCode.Success)
  }

}
