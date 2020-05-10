package com.uptech.windalerts.alerts

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.logger.requestLogger
import com.uptech.windalerts.domain.{HttpErrorHandler, errors, secrets}
import com.uptech.windalerts.users._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.log4s.getLogger
import org.mongodb.scala.MongoClient

import scala.util.Try

object AlertsServer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- IO(getLogger.error("Starting"))
      projectId <- IO(sys.env("projectId"))
      httpErrorHandler <- IO(new HttpErrorHandler[IO])
      applePrivateKey <- IO(Try(AppleLogin.getPrivateKey(s"/app/resources/Apple-$projectId.p8"))
        .getOrElse(AppleLogin.getPrivateKey(s"src/main/resources/Apple.p8")))
      client <- IO.pure(MongoClient(com.uptech.windalerts.domain.secrets.read.surfsUp.mongodb.url))
      mongoDb <- IO(client.getDatabase(sys.env("projectId")).withCodecRegistry(com.uptech.windalerts.domain.codecs.codecRegistry))
      refreshTokensCollection  <- IO( mongoDb.getCollection[RefreshToken]("refreshTokens"))
      refreshTokensRepo <- IO( new MongoRefreshTokenRepositoryAlgebra(refreshTokensCollection))
      usersCollection  <- IO( mongoDb.getCollection[UserT]("users"))
      userRepository <- IO( new MongoUserRepository(usersCollection))
      credentialsCollection  <- IO( mongoDb.getCollection[Credentials]("credentials"))
      credentialsRepository <- IO( new MongoCredentialsRepository(credentialsCollection))
      fbcredentialsCollection  <- IO( mongoDb.getCollection[FacebookCredentialsT]("facebookCredentials"))
      fbcredentialsRepository <- IO( new MongoFacebookCredentialsRepository(fbcredentialsCollection))
      applecredentialsCollection  <- IO( mongoDb.getCollection[AppleCredentials]("appleCredentials"))
      applecredentialsRepository <- IO( new MongoAppleCredentialsRepositoryAlgebra(applecredentialsCollection))
      feedbackColl  <- IO( mongoDb.getCollection[Feedback]("feedbacks"))
      feedbackRepository <- IO( new MongoFeedbackRepository(feedbackColl))
      alertsCollection  <- IO( mongoDb.getCollection[AlertT]("alerts"))
      alertsRepository <- IO( new MongoAlertsRepositoryAlgebra(alertsCollection))
      alertService <- IO(new AlertsService[IO](alertsRepository))
      auth <- IO(new Auth(refreshTokensRepo))
      androidPublisher <- IO(AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL))
      usersService <- IO( new UserService(userRepository, credentialsRepository, applecredentialsRepository, fbcredentialsRepository, alertsRepository, feedbackRepository, secrets.read.surfsUp.facebook.key, androidPublisher, applePrivateKey))
      alertsEndPoints <- IO(new AlertsEndpoints(alertService, usersService, auth, httpErrorHandler))
      httpApp <- IO(Logger.httpApp(false, true, logAction = requestLogger)(errors.errorMapper(Router(
        "/v1/users/alerts" -> auth.middleware(alertsEndPoints.allUsersService())
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
