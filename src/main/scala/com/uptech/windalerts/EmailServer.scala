package com.uptech.windalerts

import cats.Monad
import cats.effect._
import com.uptech.windalerts.core.otp.{OTPService, OTPWithExpiry}
import com.uptech.windalerts.infrastructure.EmailSender
import com.uptech.windalerts.infrastructure.endpoints.EmailEndpoints
import com.uptech.windalerts.infrastructure.repositories.mongo.{DBOTPWithExpiry, MongoOtpRepository, Repos}
import org.http4s.{Response, Status}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Router, Server => H4Server}

object EmailServer extends IOApp {

  def createServer[F[_] : ContextShift : ConcurrentEffect : Timer]()(implicit M:Monad[F]): Resource[F, H4Server[F]] =
    for {
      _ <- Resource.pure(())
      db = Repos.acquireDb(sys.env("MONGO_DB_URL"))
      emailSender = new EmailSender[F](sys.env("EMAIL_KEY"))
      otpRepositoy = new MongoOtpRepository[F](db.getCollection[DBOTPWithExpiry]("otp"))
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