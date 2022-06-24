package com.uptech.windalerts

import cats.Monad
import cats.effect._
import com.uptech.windalerts.core.otp.OTPService
import com.uptech.windalerts.infrastructure.Environment.{EnvironmentAsk, EnvironmentIOAsk}
import com.uptech.windalerts.infrastructure.{Environment, SendInBlueEmailSender}
import com.uptech.windalerts.infrastructure.endpoints.EmailEndpoints
import com.uptech.windalerts.infrastructure.repositories.mongo.{DBOTPWithExpiry, MongoOtpRepository, Repos}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._
import org.http4s.server.{Router, Server => H4Server}
import org.http4s.{Response, Status}

object EmailServer extends IOApp {
  implicit val configEnv = new EnvironmentIOAsk(Environment(Repos.acquireDb(sys.env("MONGO_DB_URL"))))

  def createServer[F[_] : EnvironmentAsk : ContextShift : ConcurrentEffect : Timer]()(implicit M: Monad[F]): Resource[F, H4Server] =
    for {
      _ <- Resource.pure(())
      emailSender = new SendInBlueEmailSender[F](sys.env("EMAIL_KEY"))
      otpRepositoy = new MongoOtpRepository[F]()
      otpService = new OTPService[F](otpRepositoy, emailSender)

      httpApp = Router(
        "/v1/email" -> new EmailEndpoints[F](otpService).allRoutes(),
      ).orNotFound
      server <- BlazeServerBuilder[F]
        .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
        .withHttpApp(httpApp)
        .withServiceErrorHandler(_ => {
          case e: Throwable =>
            logger.error("Exception ", e)
            M.pure(Response[F](status = Status.InternalServerError))
        })
        .resource
    } yield server

  def run(args: List[String]): IO[ExitCode] = createServer.use(_ => IO.never).as(ExitCode.Success)


}