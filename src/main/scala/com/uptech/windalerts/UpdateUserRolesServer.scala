package com.uptech.windalerts
import cats.effect._
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.otp.OTPWithExpiry
import com.uptech.windalerts.core.social.subscriptions.{PurchaseToken, SocialPlatformSubscriptionsService}
import com.uptech.windalerts.core.user.{UserRolesService, UserT}
import com.uptech.windalerts.infrastructure.endpoints.{UpdateUserRolesEndpoints, errors}
import com.uptech.windalerts.infrastructure.repositories.mongo._
import com.uptech.windalerts.infrastructure.social.subscriptions._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Router, Server => H4Server}

object UpdateUserRolesServer extends IOApp {
  def createServer[F[_] : ContextShift : ConcurrentEffect : Timer](): Resource[F, H4Server[F]] =
    for {
      _ <- Resource.pure(())
      db = Repos.acquireDb(sys.env("MONGO_DB_URL"))
      otpRepositoy = new MongoOtpRepository[F](db.getCollection[OTPWithExpiry]("otp"))
      usersRepository = new MongoUserRepository[F](db.getCollection[DBUser]("users"))
      androidPurchaseRepository = new MongoPurchaseTokenRepository[F](db.getCollection[PurchaseToken]("androidPurchases"))
      applePurchaseRepository = new MongoPurchaseTokenRepository[F](db.getCollection[PurchaseToken]("applePurchases"))
      alertsRepository = new MongoAlertsRepository[F](db.getCollection[DBAlert]("alerts"))

      androidPublisher = AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL)
      appleSubscription = new AppleSubscription[F](sys.env("APPLE_APP_SECRET"))
      androidSubscription = new AndroidSubscription[F](androidPublisher)
      subscriptionsService = new SocialPlatformSubscriptionsServiceImpl[F](applePurchaseRepository, androidPurchaseRepository, appleSubscription, androidSubscription)
      userRolesService = new UserRolesService[F](alertsRepository, usersRepository, otpRepositoy, new SocialPlatformSubscriptionsService[F](subscriptionsService))
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
