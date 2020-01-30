package com.uptech.windalerts.status

import cats.effect._
import cats.implicits._
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.domain.domain.BeachId
import com.uptech.windalerts.domain.{HttpErrorHandler, secrets, swellAdjustments}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Server => H4Server}
import org.log4s.getLogger

object Main extends IOApp {
  def createServer[F[_] : ContextShift : ConcurrentEffect : Timer]: Resource[F, H4Server[F]] = {

    val apiKey = ""
    implicit val backend = HttpURLConnectionBackend()
    for {
      conf <- Resource.pure(secrets.read)
      beaches = new BeachService[F](new WindsService[F](apiKey), new TidesService[F](apiKey), new SwellsService[F](apiKey, swellAdjustments.read))

      httpApp = new StatusEndPoints[F](beaches, new HttpErrorHandler[F]).allRoutes().orNotFound

      server <- BlazeServerBuilder[F]
        .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
        .withHttpApp(httpApp)
        .resource
    } yield server
  }

  private val logger = getLogger

  //  def allRoutes(B: BeachService[F], H: HttpErrorHandler[IO]) = HttpRoutes.of[IO] {
  //    case GET -> Root / "v1" / "beaches" / IntVar(id) / "currentStatus" =>
  //      Ok(B.get(BeachId(id)))
  //    case GET -> Root / "beaches" / IntVar(id) / "currentStatus" =>
  //      Ok(B.get(BeachId(id)))
  //  }.orNotFound
  //
  //  def run(args: List[String]): IO[ExitCode] = {
  //    for {
  //      _ <- Resource.liftF(IO(getLogger.error("Starting"))
  //        conf <- Resource.liftF(IO(secrets.read)
  //        apiKey <- Resource.liftF(IO(conf.surfsUp.willyWeather.key)
  //        beaches <- Resource.liftF(IO(Beaches.ServiceImpl(Winds.impl(apiKey), Swells.impl(apiKey, swellAdjustments.read), Tides.impl(apiKey)))
  //        httpErrorHandler <- Resource.liftF(IO(new HttpErrorHandler[IO])
  //        server <- BlazeServerBuilder[IO]
  //      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
  //      .withHttpApp(allRoutes(beaches, httpErrorHandler))
  //      .serve
  //       .compile
  //       .drain
  //       .as(ExitCode.Success)
  //    } yield server
  //  }

}

class StatusEndPoints[F[_] : Sync](B: BeachService[F], H: HttpErrorHandler[F]) extends Http4sDsl[F] {
  def allRoutes() = HttpRoutes.of[F] {
    case GET -> Root / "beaches" / IntVar(id) / "currentStatus" =>
      getStatus(B, id, H)
    case GET -> Root / "v1" / "beaches" / IntVar(id) / "currentStatus" =>
      getStatus(B, id, H)
  }

  private def getStatus(B: BeachService[F], id: Int, H: HttpErrorHandler[F]) = {
    val eitherStatus = for {
      status <- B.get(BeachId(id))
    } yield status
    eitherStatus.value.flatMap {
      case Right(_) => Ok()
      case Left(error) => H.handleThrowable(error)
    }
  }
}