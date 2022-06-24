package com.uptech.windalerts

import cats.Monad
import cats.effect._
import com.uptech.windalerts.core.user.UserRolesService
import com.uptech.windalerts.infrastructure.Environment
import com.uptech.windalerts.infrastructure.Environment.{EnvironmentAsk, EnvironmentIOAsk}
import com.uptech.windalerts.infrastructure.endpoints.{UpdateUserRolesEndpoints, errors}
import com.uptech.windalerts.infrastructure.repositories.mongo._
import com.uptech.windalerts.infrastructure.social.subscriptions._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._
import org.http4s.server.{Router, Server => H4Server}
import org.http4s.{Response, Status}

object UpdateUserRolesServer extends IOApp {
  implicit val configEnv = new EnvironmentIOAsk(Environment(Repos.acquireDb(sys.env("MONGO_DB_URL"))))

  def createServer[F[_] : EnvironmentAsk:ContextShift : ConcurrentEffect : Timer]()(implicit M: Monad[F]): Resource[F, H4Server] =
    for {
      _ <- Resource.pure(())
      db = Repos.acquireDb(sys.env("MONGO_DB_URL"))
      otpRepositoy = new MongoOtpRepository[F]()
      usersRepository = new MongoUserRepository[F]()
      androidPurchaseRepository = new MongoPurchaseTokenRepository[F]("androidPurchases")
      applePurchaseRepository = new MongoPurchaseTokenRepository[F]("applePurchases")
      alertsRepository = new MongoAlertsRepository[F]()

      androidPublisher = AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL)
      appleSubscription = new AppleSubscription[F](sys.env("APPLE_APP_SECRET"))
      androidSubscription = new AndroidSubscription[F](androidPublisher)
      subscriptionsService = new AllSocialPlatformSubscriptionsProviders[F](applePurchaseRepository, androidPurchaseRepository, appleSubscription, androidSubscription)
      userRolesService = new UserRolesService[F](alertsRepository, usersRepository, otpRepositoy, subscriptionsService)
      endpoints = new UpdateUserRolesEndpoints[F](userRolesService)

      httpApp = Router(
        "/v1/users/roles" -> endpoints.endpoints(),
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
