package com.uptech.windalerts

import cats.effect.{IO, _}
import cats.implicits._
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.credentials.{AppleCredentials, Credentials, FacebookCredentials, UserCredentialService}
import com.uptech.windalerts.core.otp.{OTPService, OTPWithExpiry}
import com.uptech.windalerts.core.refresh.tokens.RefreshToken
import com.uptech.windalerts.core.social.subscriptions.{AndroidToken, AppleToken}
import com.uptech.windalerts.core.user.{AuthenticationService, UserRolesService, UserService, UserT}
import com.uptech.windalerts.infrastructure.{EmailSender, GooglePubSubEventpublisher}
import com.uptech.windalerts.infrastructure.endpoints.logger._
import com.uptech.windalerts.infrastructure.endpoints.{UpdateUserRolesEndpoints, errors}
import com.uptech.windalerts.infrastructure.repositories.mongo._
import com.uptech.windalerts.infrastructure.social.subscriptions._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.log4s.getLogger

object UpdateUserRolesServer extends IOApp {

  implicit val backend = HttpURLConnectionBackend()

  override def run(args: List[String]): IO[ExitCode] = for {
    _ <- IO(getLogger.info("Starting"))
    db = Repos.acquireDb
    otpRepositoy = new MongoOtpRepository[IO](db.getCollection[OTPWithExpiry]("otp"))
    usersRepository = new MongoUserRepository(db.getCollection[UserT]("users"))
    androidPurchaseRepository = new MongoAndroidPurchaseRepository(db.getCollection[AndroidToken]("androidPurchases"))
    applePurchaseRepository = new MongoApplePurchaseRepository(db.getCollection[AppleToken]("applePurchases"))
    alertsRepository = new MongoAlertsRepository(db.getCollection[Alert]("alerts"))
    refreshTokenRepository = new MongoRefreshTokenRepository(db.getCollection[RefreshToken]("refreshTokens"))

    androidPublisher = AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL)
    appleSubscription <- IO(new AppleSubscription[IO]())
    androidSubscription <- IO(new AndroidSubscription[IO](androidPublisher))
    subscriptionsService <- IO(new SocialPlatformSubscriptionsServiceImpl[IO](applePurchaseRepository, androidPurchaseRepository, appleSubscription, androidSubscription))
    userRolesService <- IO(new UserRolesService[IO](applePurchaseRepository, androidPurchaseRepository, alertsRepository, usersRepository, otpRepositoy, subscriptionsService, refreshTokenRepository))
    endpoints <- IO(new UpdateUserRolesEndpoints[IO](userRolesService))

    httpApp <- IO(errors.errorMapper(
      Router(
        "/v1/users/roles" -> endpoints.endpoints(),
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
