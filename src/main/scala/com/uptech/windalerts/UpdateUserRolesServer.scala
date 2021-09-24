package com.uptech.windalerts
import cats.effect.Resource.eval
import cats.effect._
import com.typesafe.config.ConfigFactory.parseFileAnySyntax
import com.uptech.windalerts.config._
import com.uptech.windalerts.config.secrets.SurfsUpSecret
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.otp.OTPWithExpiry
import com.uptech.windalerts.core.refresh.tokens.RefreshToken
import com.uptech.windalerts.core.social.subscriptions.{AndroidToken, AppleToken}
import com.uptech.windalerts.core.user.{UserRolesService, UserT}
import com.uptech.windalerts.infrastructure.endpoints.{UpdateUserRolesEndpoints, errors}
import com.uptech.windalerts.infrastructure.repositories.mongo._
import com.uptech.windalerts.infrastructure.social.subscriptions._
import io.circe.config.parser.decodePathF
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Router, Server => H4Server}

object UpdateUserRolesServer extends IOApp {
  def createServer[F[_] : ContextShift : ConcurrentEffect : Timer](): Resource[F, H4Server[F]] =
    for {
      surfsUp <- eval(decodePathF[F, SurfsUpSecret](parseFileAnySyntax(secrets.getConfigFile()), "surfsUp"))
      db = Repos.acquireDb(surfsUp.mongodb.url)
      otpRepositoy = new MongoOtpRepository[F](db.getCollection[OTPWithExpiry]("otp"))
      usersRepository = new MongoUserRepository[F](db.getCollection[UserT]("users"))
      androidPurchaseRepository = new MongoAndroidPurchaseRepository[F](db.getCollection[AndroidToken]("androidPurchases"))
      applePurchaseRepository = new MongoApplePurchaseRepository[F](db.getCollection[AppleToken]("applePurchases"))
      alertsRepository = new MongoAlertsRepository[F](db.getCollection[Alert]("alerts"))
      refreshTokenRepository = new MongoRefreshTokenRepository[F](db.getCollection[RefreshToken]("refreshTokens"))

      androidPublisher = AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL)
      appleSubscription = new AppleSubscription[F](surfsUp.apple.appSecret)
      androidSubscription = new AndroidSubscription[F](androidPublisher)
      subscriptionsService = new SocialPlatformSubscriptionsServiceImpl[F](applePurchaseRepository, androidPurchaseRepository, appleSubscription, androidSubscription)
      userRolesService = new UserRolesService[F](applePurchaseRepository, androidPurchaseRepository, alertsRepository, usersRepository, otpRepositoy, subscriptionsService, refreshTokenRepository, surfsUp.apple.appSecret)
      endpoints = new UpdateUserRolesEndpoints[F](userRolesService)

      httpApp = Router(
        "/v1/users/roles" -> endpoints.endpoints(),
      ).orNotFound
      server <- BlazeServerBuilder[F]
        .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
        .withHttpApp(new errors[F].errorMapper(httpApp))
        .resource
    } yield server

  def run(args: List[String]): IO[ExitCode] = createServer.use(_ => IO.never).as(ExitCode.Success)
}
