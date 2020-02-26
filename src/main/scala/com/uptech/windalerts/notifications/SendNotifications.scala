package com.uptech.windalerts.notifications

import java.io.FileInputStream

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.softwaremill.sttp.HttpURLConnectionBackend
import com.uptech.windalerts.alerts.{AlertsService, MongoAlertsRepositoryAlgebra}
import com.uptech.windalerts.domain._
import com.uptech.windalerts.domain.domain.{AlertT, UserT}
import com.uptech.windalerts.status.{BeachService, SwellsService, TidesService, WindsService}
import com.uptech.windalerts.users.{MongoUserRepository, UserRepositoryAlgebra}
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io.{->, /, GET, Ok, _}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}

import scala.concurrent.duration.Duration
import scala.util.Try

object SendNotifications extends IOApp {

  private val logger = getLogger

  logger.error("Starting")


  val dbWithAuthIO = for {
    projectId     <- IO(sys.env("projectId"))
    credentials   <- IO(Try(GoogleCredentials.fromStream(new FileInputStream(s"/app/resources/$projectId.json")))
                            .getOrElse(GoogleCredentials.getApplicationDefault))
    options       <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId(projectId).build)
    _             <- IO(FirebaseApp.initializeApp(options))
    db            <- IO(FirestoreClient.getFirestore)
    notifications <- IO(FirebaseMessaging.getInstance)
  } yield (db, notifications)

  val dbWithAuth = dbWithAuthIO.unsafeRunSync()
  implicit val backend = HttpURLConnectionBackend()

  val conf = secrets.read
  val appConf = config.read
  val key = conf.surfsUp.willyWeather.key
  val beachSeq = beaches.read
  logger.error(s"beachSeq $beachSeq")
  val adjustments = swellAdjustments.read
  val beachesService = new BeachService[IO](new WindsService[IO](key), new TidesService[IO](key), new SwellsService[IO](key, swellAdjustments.read))


  implicit val httpErrorHandler: HttpErrorHandler[IO] = new HttpErrorHandler[IO]



  val client: MongoClient = MongoClient(appConf.surfsUp.mongodb.url)
  val db: MongoDatabase = client.getDatabase("surfsup").withCodecRegistry(com.uptech.windalerts.domain.codecs.mNotificationCodecRegistry)
  val usersCollection:MongoCollection[UserT] =  db.getCollection("users")
  val usersRepo = new MongoUserRepository(usersCollection)
  private val coll: MongoCollection[domain.Notification] = db.getCollection("notifications")
  val alertColl: MongoCollection[AlertT]  = db.getCollection("alerts")
  val alertsRepo = new MongoAlertsRepositoryAlgebra(alertColl)
  val alerts = new AlertsService.ServiceImpl(alertsRepo)
  val notifications = new Notifications(alerts, beachesService, beachSeq, usersRepo, dbWithAuth._2, httpErrorHandler, notificationsRepository = new MongoNotificationsRepository(coll), config = config.read)

  def allRoutes(A: AlertsService.Service, B: BeachService[IO], UR: UserRepositoryAlgebra, firebaseMessaging: FirebaseMessaging, H: HttpErrorHandler[IO]) = HttpRoutes.of[IO] {
    case GET -> Root / "notify" => {
      val res = notifications.sendNotification
      val either = res.value.unsafeRunSync()
      Ok()
    }
  }.orNotFound


  def run(args: List[String]): IO[ExitCode] = {

    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withResponseHeaderTimeout(Duration(5, "min"))
      .withIdleTimeout(Duration(8, "min"))
      .withHttpApp(allRoutes(alerts, beachesService, usersRepo, dbWithAuth._2, httpErrorHandler))
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }

}