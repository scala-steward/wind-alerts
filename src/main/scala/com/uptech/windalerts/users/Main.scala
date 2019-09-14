//package com.uptech.windalerts.users
//
//import java.io.FileInputStream
//
//import cats.effect.{ExitCode, IO, IOApp}
//import com.google.auth.oauth2.GoogleCredentials
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.{FirebaseApp, FirebaseOptions}
//import org.http4s.HttpRoutes
//import org.http4s.dsl.impl.Root
//import org.http4s.dsl.io.{->, /, GET, Ok}
//import org.http4s.server.blaze.BlazeServerBuilder
//import org.log4s.getLogger
//
//object Main extends IOApp {
//  im
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
//  val users = new Users.FireStoreBackedService(auth)
//  logger.error("users " + users)
//
//  def run(args: List[String]): IO[ExitCode] =
//    BlazeServerBuilder[IO]
//      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
//      .withHttpApp(sendAlertsRoute(users))
//      .serve
//      .compile
//      .drain
//      .as(ExitCode.Success)
//
//  def sendAlertsRoute(U: Users.Service) = HttpRoutes.of[IO] {
//
//    case GET -> Root / "users" / email / password => {
//      logger.error("Called " + email + password)
//      Ok(U.registerUser(email, password))
//    }
//
//  }.orNotFound
//
//
//
//}
