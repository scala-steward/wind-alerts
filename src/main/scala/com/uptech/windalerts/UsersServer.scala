package com.uptech.windalerts

import cats.effect.Resource.eval
import cats.effect._
import com.softwaremill.sttp.quick.backend
import com.typesafe.config.ConfigFactory.parseFileAnySyntax
import com.uptech.windalerts.config._
import com.uptech.windalerts.config.beaches.{Beaches, _}
import com.uptech.windalerts.config.secrets.SurfsUpSecret
import com.uptech.windalerts.config.swellAdjustments.Adjustments
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
import com.uptech.windalerts.infrastructure.beaches.{WWBackedSwellsService, WWBackedTidesService, WWBackedWindsService}
import com.uptech.windalerts.infrastructure.endpoints.{AlertsEndpoints, BeachesEndpoints, SwaggerEndpoints, UsersEndpoints, errors}
import com.uptech.windalerts.infrastructure.repositories.mongo.{MongoAlertsRepository, MongoAndroidPurchaseRepository, MongoApplePurchaseRepository, MongoCredentialsRepository, MongoOtpRepository, MongoRefreshTokenRepository, MongoSocialCredentialsRepository, MongoUserRepository, Repos}
import com.uptech.windalerts.infrastructure.social.login.{AppleLogin, FacebookLogin}
import com.uptech.windalerts.infrastructure.social.subscriptions.{AndroidPublisherHelper, AndroidSubscription, AppleSubscription, ApplicationConfig, SocialPlatformSubscriptionsServiceImpl}
import io.circe.config.parser.decodePathF
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Router, Server => H4Server}

object UsersServer extends IOApp {
  def createServer[F[_] : ContextShift : ConcurrentEffect : Timer](): Resource[F, H4Server[F]] =
    for {
      surfsUp <- eval(decodePathF[F, SurfsUpSecret](parseFileAnySyntax(secrets.getConfigFile()), "surfsUp"))
      beaches <- eval(decodePathF[F, Beaches](parseFileAnySyntax(config.getConfigFile("beaches.json")), "surfsUp"))
      swellAdjustments <- eval(decodePathF[F, Adjustments](parseFileAnySyntax(config.getConfigFile("swellAdjustments.json")), "surfsUp"))
      willyWeatherAPIKey = surfsUp.willyWeather.key


      projectId = sys.env("projectId")
      googlePublisher = new GooglePubSubEventpublisher[F](projectId)
      androidPublisher = AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL)

      db = Repos.acquireDb(surfsUp.mongodb.url)
      refreshTokenRepository = new MongoRefreshTokenRepository[F](db.getCollection[RefreshToken]("refreshTokens"))
      otpRepositoy = new MongoOtpRepository[F](db.getCollection[OTPWithExpiry]("otp"))
      usersRepository = new MongoUserRepository[F](db.getCollection[UserT]("users"))
      credentialsRepository = new MongoCredentialsRepository[F](db.getCollection[Credentials]("credentials"))
      facebookCredentialsRepository = new MongoSocialCredentialsRepository[F, FacebookCredentials](db.getCollection[FacebookCredentials]("facebookCredentials"))
      appleCredentialsRepository = new MongoSocialCredentialsRepository[F, AppleCredentials](db.getCollection[AppleCredentials]("appleCredentials"))
      androidPurchaseRepository = new MongoAndroidPurchaseRepository[F](db.getCollection[AndroidToken]("androidPurchases"))
      applePurchaseRepository = new MongoApplePurchaseRepository[F](db.getCollection[AppleToken]("applePurchases"))
      alertsRepository = new MongoAlertsRepository[F](db.getCollection[Alert]("alerts"))
      applePlatform = new AppleLogin[F](config.getConfigFile(s"Apple-$projectId.p8", "Apple.p8"))
      facebookPlatform = new FacebookLogin[F](surfsUp.facebook.key)
      beachService = new BeachService[F](
        new WWBackedWindsService[F](willyWeatherAPIKey),
        new WWBackedTidesService[F](willyWeatherAPIKey, beaches.toMap()),
        new WWBackedSwellsService[F](willyWeatherAPIKey, swellAdjustments))
      auth = new AuthenticationService[F](refreshTokenRepository)
      emailSender = new EmailSender[F](surfsUp.email.apiKey)
      otpService = new OTPService(otpRepositoy, emailSender)
      userCredentialsService = new UserCredentialService[F](facebookCredentialsRepository, appleCredentialsRepository, credentialsRepository, usersRepository, refreshTokenRepository, emailSender)
      usersService = new UserService[F](usersRepository, userCredentialsService, auth, refreshTokenRepository, googlePublisher)
      socialLoginService = new SocialLoginService[F](applePlatform, facebookPlatform,  facebookCredentialsRepository, appleCredentialsRepository, usersRepository, usersService, userCredentialsService)

      appleSubscription = new AppleSubscription[F](surfsUp.apple.appSecret)
      androidSubscription = new AndroidSubscription[F](androidPublisher)
      subscriptionsService = new SocialPlatformSubscriptionsServiceImpl[F](applePurchaseRepository, androidPurchaseRepository, appleSubscription, androidSubscription)
      userRolesService = new UserRolesService[F](applePurchaseRepository, androidPurchaseRepository, alertsRepository, usersRepository, otpRepositoy, subscriptionsService, refreshTokenRepository, surfsUp.apple.appSecret)

      endpoints = new UsersEndpoints[F](userCredentialsService, usersService, socialLoginService, userRolesService, subscriptionsService, otpService)
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
        .resource
    } yield server

  def run(args: List[String]): IO[ExitCode] = createServer.use(_ => IO.never).as(ExitCode.Success)

}
