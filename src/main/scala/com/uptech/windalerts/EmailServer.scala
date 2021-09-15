package com.uptech.windalerts

import cats.effect.Resource.eval
import cats.effect._
import com.softwaremill.sttp.quick.backend
import com.typesafe.config.ConfigFactory.parseFileAnySyntax
import com.uptech.windalerts.config._
import com.uptech.windalerts.config.beaches.{Beaches, _}
import com.uptech.windalerts.config.secrets.SurfsUp
import com.uptech.windalerts.config.swellAdjustments.Adjustments
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.core.otp.{OTPService, OTPWithExpiry, OtpRepository}
import com.uptech.windalerts.core.user.UserT
import com.uptech.windalerts.infrastructure.EmailSender
import com.uptech.windalerts.infrastructure.beaches.{WWBackedSwellsService, WWBackedTidesService, WWBackedWindsService}
import com.uptech.windalerts.infrastructure.endpoints.{BeachesEndpoints, EmailEndpoints, logger}
import com.uptech.windalerts.infrastructure.repositories.mongo.{MongoOtpRepository, MongoUserRepository, Repos}
import io.circe.config.parser.decodePathF
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.RequestLogger
import org.http4s.server.{Router, Server => H4Server}

object EmailServer extends IOApp {

  def createServer[F[_] : ContextShift : ConcurrentEffect : Timer](): Resource[F, H4Server[F]] =
    for {
      surfsUp <- eval(decodePathF[F, SurfsUp](parseFileAnySyntax(secrets.getConfigFile()), "surfsUp"))
      db = Repos.acquireDb(surfsUp.mongodb.url)
      emailSender = new EmailSender[F](surfsUp.email.apiKey)
      otpRepositoy = new MongoOtpRepository[F](db.getCollection[OTPWithExpiry]("otp"))
      otpService = new OTPService[F](otpRepositoy, emailSender)

      httpApp = Router(
        "/v1/email" -> new EmailEndpoints[F](otpService).allRoutes(),
      ).orNotFound
      server <- BlazeServerBuilder[F]
        .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
        .withHttpApp(RequestLogger.httpApp( logBody = true, logHeaders = true, logAction = new logger[F]().requestLogger)(httpApp))
        .resource
    } yield server

  def run(args: List[String]): IO[ExitCode] = createServer.use(_ => IO.never).as(ExitCode.Success)


}