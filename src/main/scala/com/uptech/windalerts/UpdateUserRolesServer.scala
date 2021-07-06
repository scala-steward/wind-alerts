package com.uptech.windalerts

import cats.effect.{IO, _}
import cats.implicits._
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.SendNotifications.{db, repos}
import com.uptech.windalerts.core.credentials.{AppleCredentials, Credentials, FacebookCredentials, UserCredentialService}
import com.uptech.windalerts.core.otp.{OTPService, OTPWithExpiry}
import com.uptech.windalerts.core.refresh.tokens.RefreshToken
import com.uptech.windalerts.core.user.{AuthenticationService, UserRolesService, UserService, UserT}
import com.uptech.windalerts.infrastructure.EmailSender
import com.uptech.windalerts.infrastructure.endpoints.logger._
import com.uptech.windalerts.infrastructure.endpoints.{UpdateUserRolesEndpoints, errors}
import com.uptech.windalerts.infrastructure.repositories.mongo.{LazyRepos, MongoCredentialsRepository, MongoOtpRepository, MongoRefreshTokenRepository, MongoSocialCredentialsRepository, MongoUserRepository, Repos}
import com.uptech.windalerts.infrastructure.social.subscriptions.{AndroidSubscription, AppleSubscription, SubscriptionsServiceImpl}
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.log4s.getLogger

object UpdateUserRolesServer extends IOApp {

  implicit val backend = HttpURLConnectionBackend()

  override def run(args: List[String]): IO[ExitCode] = for {
    _ <- IO(getLogger.error("Starting"))
    db = Repos.acquireDb

    repos = new LazyRepos()
    refreshTokenRepository = new MongoRefreshTokenRepository(db.getCollection[RefreshToken]("refreshTokens"))

    authService <- IO(new AuthenticationService(refreshTokenRepository))
    emailConf = com.uptech.windalerts.config.secrets.read.surfsUp.email
    emailSender = new EmailSender[IO](emailConf.apiKey)
    otpRepositoy = new MongoOtpRepository[IO](db.getCollection[OTPWithExpiry]("otp"))
    usersRepository = new MongoUserRepository(db.getCollection[UserT]("users"))
    credentialsRepository = new MongoCredentialsRepository(db.getCollection[Credentials]("credentials"))
    facebookCredentialsRepository = new MongoSocialCredentialsRepository[IO, FacebookCredentials](db.getCollection[FacebookCredentials]("facebookCredentials"))
    appleCredentialsRepository = new MongoSocialCredentialsRepository[IO, AppleCredentials](db.getCollection[AppleCredentials]("appleCredentials"))

    otpService = new OTPService[IO](otpRepositoy, emailSender)

    userCredentialsService <- IO(new UserCredentialService[IO](facebookCredentialsRepository, appleCredentialsRepository, credentialsRepository, usersRepository, refreshTokenRepository, emailSender))
    userService <- IO(new UserService[IO](usersRepository, repos, userCredentialsService, otpService, authService, refreshTokenRepository))
    appleSubscription <- IO(new AppleSubscription[IO]())
    androidSubscription <- IO(new AndroidSubscription[IO](repos))
    subscriptionsService <- IO(new SubscriptionsServiceImpl[IO](appleSubscription, androidSubscription, repos))
    userRolesService <- IO(new UserRolesService[IO](usersRepository, otpRepositoy, repos, subscriptionsService, userService))
    endpoints <- IO(new UpdateUserRolesEndpoints[IO](userRolesService))


    httpApp <- IO(errors.errorMapper(Logger.httpApp(true, true, logAction = requestLogger)(
      Router(
        "/v1/users/roles" -> endpoints.endpoints(),
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
