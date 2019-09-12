package com.uptech.windalerts.alerts

import java.io.{File, FileInputStream, InputStream}

import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.domain.Domain
import com.uptech.windalerts.domain.Domain.BeachId
import com.uptech.windalerts.status.{Beaches, Swells, Tides, Winds}
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
import com.uptech.windalerts.users.Users
object Main extends IOApp {

  import com.uptech.windalerts.domain.DomainCodec._

  private val logger = getLogger

  val credentials = GoogleCredentials.fromStream(new FileInputStream("wind-alerts-staging.json"))
  val options = new FirebaseOptions.Builder().setCredentials(credentials).setProjectId("wind-alerts-staging").build
  FirebaseApp.initializeApp(options)
  val db = FirestoreClient.getFirestore
  val auth = FirebaseAuth.getInstance

  val beaches = Beaches.ServiceImpl(Winds.impl, Swells.impl, Tides.impl)
  val alerts = new Alerts.FireStoreBackedService(db)


  val users = new Users.FireStoreBackedService(auth)

  import com.uptech.windalerts.domain.DomainCodec._

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withHttpApp(sendAlertsRoute(alerts, beaches, users))
      .serve
      .compile
      .drain
      .as(ExitCode.Success)

  def sendAlertsRoute(A: Alerts.Service, B: Beaches.Service, U : Users.Service) = HttpRoutes.of[IO] {

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
    case req@POST -> Root / "alerts" =>
      val a = for {
        header <- IO.fromEither(req.headers.get(Authorization).toRight(new RuntimeException("Couldn't find an Authorization header")))
        u <- U.verify(header.value)
        _ <- IO(println(header.value))
        alert <- req.as[Domain.Alert]
        resp <- A.save(alert)
      } yield (u.getUid)
      Ok(a)
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


}