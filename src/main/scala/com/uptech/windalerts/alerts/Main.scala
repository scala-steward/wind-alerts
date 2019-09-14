//package com.uptech.windalerts.alerts
//
//import java.io.FileInputStream
//
//import cats.effect.{ExitCode, IO, IOApp}
//import com.google.auth.oauth2.GoogleCredentials
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.cloud.FirestoreClient
//import com.google.firebase.{FirebaseApp, FirebaseOptions}
//import com.uptech.windalerts.status.{Beaches, Swells, Tides, Winds}
//import com.uptech.windalerts.users.Users
//import org.http4s.HttpRoutes
//import org.http4s.dsl.impl.Root
//import org.http4s.dsl.io.{->, /, GET, Ok, POST}
//import org.http4s.headers.Authorization
//import org.http4s.server.blaze.BlazeServerBuilder
//import org.log4s.getLogger
//
//object Main extends IOApp {
//
//  private val logger = getLogger
//
//  logger.error("Starting")
//  val credentials = GoogleCredentials.fromStream(new FileInputStream("wind-alerts-staging.json"))
//  logger.error("Credentials")
//  val options = new FirebaseOptions.Builder().setCredentials(credentials).setProjectId("wind-alerts-staging").build
//  logger.error("Options")
//  FirebaseApp.initializeApp(options)
//  val auth = FirebaseAuth.getInstance
//  logger.error("auth")
//
//  val db = FirestoreClient.getFirestore
//
//  val beaches = Beaches.ServiceImpl(Winds.impl, Swells.impl, Tides.impl)
//  val alerts = new Alerts.FireStoreBackedService(db)
//
//
//  val users = new Users.FireStoreBackedService(auth)
//
//  def run(args: List[String]): IO[ExitCode] =
//    BlazeServerBuilder[IO]
//      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
//      .withHttpApp(sendAlertsRoute(alerts, beaches, users))
//      .serve
//      .compile
//      .drain
//      .as(ExitCode.Success)
//
//  def sendAlertsRoute(A: Alerts.Service, B: Beaches.Service, U : Users.Service) = HttpRoutes.of[IO] {
//
//    case GET -> Root / "notify" => {
//      val usersToBeNotified = for {
//        alerts <- A.getAllForDay
//        alertsByBeaches <- IO(alerts.groupBy(_.beachId).map(
//          kv => {
//            (B.get(BeachId(kv._1.toInt)), kv._2)
//          }))
//        asIOMap <- toIOMap(alertsByBeaches)
//        alertsToBeNotified <- IO(asIOMap.map(kv => (kv._1, kv._2.filter(_.isToBeNotified(kv._1)))))
//        usersToBeNotified <- IO(alertsToBeNotified.values.flatMap(elem => elem).map(_.owner).toSeq)
//        printIO <- IO("" + usersToBeNotified)
//      } yield Domain.Alerts(alertsToBeNotified.values.flatMap(e=>e).toSeq)
//      Ok(usersToBeNotified)
//    }
//    case req@POST -> Root / "alerts" =>
//      val a = for {
//        header <- IO.fromEither(req.headers.get(Authorization).toRight(new RuntimeException("Couldn't find an Authorization header")))
//        u <- U.verify(header.value)
//        _ <- IO(println(header.value))
//        alert <- req.as[Domain.Alert]
//        resp <- A.save(alert)
//      } yield (u.getUid)
//      Ok(a)
//  }.orNotFound
//
//
//  private def toIOMap(m: Map[IO[Domain.Beach], Seq[Domain.Alert]]) = {
//    m.toList.traverse {
//      case (io, s) => io.map(s2 => (s2, s))
//    }.map {
//      _.toMap
//    }
//  }
//
//  private def  toEither[T](ox: Option[T]) : Either[String, T] = {
//    if (ox.isDefined) Right(ox.get) else Left("No number")
//
//  }
//
//
//}
