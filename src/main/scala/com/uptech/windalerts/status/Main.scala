package com.uptech.windalerts.status

import scala.util.Try
// import cats.implicits._
// import org.http4s.server.blaze._
// import org.http4s.implicits._

import java.io.FileInputStream

import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.alerts.Alerts
import com.uptech.windalerts.domain.Domain
import com.uptech.windalerts.domain.Domain.BeachId
import com.uptech.windalerts.users.Users
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger


object Main extends IOApp {

  import com.uptech.windalerts.domain.DomainCodec._

  private val logger = getLogger

  logger.error("Starting")


  val dbWithAuthIO = for {
    credentials <- IO(Try(GoogleCredentials.fromStream(new FileInputStream("/app/resources/wind-alerts-staging.json")))
      .getOrElse(GoogleCredentials.getApplicationDefault))
    options <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId("wind-alerts-staging").build)
    _ <- IO(FirebaseApp.initializeApp(options))
    db <- IO(FirestoreClient.getFirestore)
    auth <- IO(FirebaseAuth.getInstance)
  } yield (db, auth)

  val dbWithAuth = dbWithAuthIO.unsafeRunSync()

  val beaches = Beaches.ServiceImpl(Winds.impl, Swells.impl, Tides.impl)
  val alerts = new Alerts.FireStoreBackedService(dbWithAuth._1)
  val users = new Users.FireStoreBackedService(dbWithAuth._2)

  def allRoutes(A: Alerts.Service, B: Beaches.Service, U : Users.Service) = HttpRoutes.of[IO] {
    case GET -> Root / "beaches" / IntVar(id) / "currentStatus" =>
      Ok(B.get(BeachId(id)))
    case GET -> Root / "notify" => {
      val usersToBeNotified = for {
        alerts <- A.getAllForDay
        alertsByBeaches <- IO(alerts.groupBy(_.beachId).map(
          kv => {
            (B.get(BeachId(kv._1.toInt)), kv._2)
          }))
        asIOMap <- toIOMap(alertsByBeaches)
        alertsToBeNotified <- IO(asIOMap.map(kv => (kv._1, kv._2.filter(_.isToBeNotified(kv._1)))))
        usersToBeNotified <- IO(alertsToBeNotified.values.flatMap(elem => elem).map(_.owner).toSeq)
        printIO <- IO("" + usersToBeNotified)
      } yield Domain.Alerts(alertsToBeNotified.values.flatMap(e=>e).toSeq)
      Ok(usersToBeNotified)
    }
    case req@GET -> Root / "alerts" =>
      val alerts = for {
        header <- IO.fromEither(req.headers.get(Authorization).toRight(new RuntimeException("Couldn't find an Authorization header")))
        u <- U.verify(header.value)
        _ <- IO(println(u.getUid))
        resp <- A.getAllForUser(u.getUid)
      } yield (resp)
      Ok(alerts)
    case req@POST -> Root / "alerts" =>
      val alert = for {
        header <- IO.fromEither(req.headers.get(Authorization).toRight(new RuntimeException("Couldn't find an Authorization header")))
        u <- U.verify(header.value)
        alert <- req.as[Domain.AlertRequest]
        resp <- A.save(alert, u.getUid)
      } yield (resp)
      Created(alert)
    case req@DELETE -> Root / "alerts" / alertId =>
      val alert = for {
        header <- IO.fromEither(req.headers.get(Authorization).toRight(new RuntimeException("Couldn't find an Authorization header")))
        u <- U.verify(header.value)
        resp <- A.delete(u.getUid, alertId)
      } yield (resp)
      val res = alert.unsafeRunSync()
      res.toOption.get.unsafeRunSync()
      NoContent()
  }.orNotFound


  private def toIOMap(m: Map[IO[Domain.Beach], Seq[Domain.Alert]]) = {
    m.toList.traverse {
      case (io, s) => io.map(s2 => (s2, s))
    }.map {
      _.toMap
    }
  }

  private def  toEither[T](ox: Option[T]) : Either[String, T] = {
    if (ox.isDefined) Right(ox.get) else Left("No number")

  }


  def run(args: List[String]): IO[ExitCode] = {

    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withHttpApp(allRoutes(alerts, beaches, users))
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }

}
