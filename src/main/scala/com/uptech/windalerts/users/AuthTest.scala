//package com.uptech.windalerts.users
//
//import cats.data.{EitherT, OptionT}
//import cats.effect.{ExitCode, IO, IOApp}
//import dev.profunktor.auth._
//import dev.profunktor.auth.jwt._
//import org.http4s.{AuthedRoutes, HttpRoutes, _}
//import org.http4s.dsl.impl.Root
//import org.http4s.dsl.io._
//import org.http4s.server.blaze.BlazeServerBuilder
//import pdi.jwt.{JwtAlgorithm, JwtClaim, _}
//import cats.effect.IO
//import cats.implicits._
//import dev.profunktor.auth._
//import dev.profunktor.auth.jwt._
//import pdi.jwt._
//import com.uptech.windalerts.domain.domain._
//import com.uptech.windalerts.domain.codecs._
//import com.uptech.windalerts.domain.{HttpErrorHandler, domain}
//import com.uptech.windalerts.status.Beaches
//import org.http4s._
//import org.http4s.dsl.io._
//import org.http4s.implicits._
//import org.http4s.implicits._
//import org.http4s.server.blaze._
//import cats.implicits._
//
//object AuthTest extends IOApp {
//
//  case class AuthUser(id: String, name: String)
//
//  // i.e. retrieve user from database
//  val authenticate: JwtClaim => IO[Option[AuthUser]] =
//    claim => AuthUser("123L", "joe").some.pure[IO]
//
//  val jwtAuth = JwtAuth(JwtSecretKey("53cr3t"), JwtAlgorithm.HS256)
//  val middleware = JwtAuthMiddleware[IO, AuthUser](jwtAuth, authenticate)
//
//
//  def allRoutes() = HttpRoutes.of[IO] {
//    //Where user is the case class User above
//    case req@POST -> Root / "register" => {
//      req.decode[domain.Credentials] { credentials =>
//        val resFut = IO.fromFuture(IO(registerIfNotRegistered(credentials).value))
//
//        resFut.unsafeRunSync() match {
//          case Left(error) => H.handleThrowable(error)
//          case Right(value) => Ok(value)
//        }
//      }
//    }
//  }
//
//
//  private def registerIfNotRegistered(credentials: domaon.Credentials) = {
//    for {
//      x <- EitherT(checkIfAlreadyRegistered(credentials))
//      y <- EitherT(register(x))
//    } yield y
//  }
//
//
//  val authedService: AuthedRoutes[AuthUser, IO] =
//    AuthedRoutes {
//      case GET -> Root / "welcome" as user => {
//        OptionT.liftF(Ok(""))
//      }
//    }
//
//
//  def securedRoutes: HttpRoutes[IO] = middleware(authedService)
//
//  def run(args: List[String]): IO[ExitCode] = {
//    val r = allRoutes() <+>  securedRoutes
//    BlazeServerBuilder[IO]
//      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
//      .withHttpApp(r.orNotFound)
//      .serve
//      .compile
//      .drain
//      .as(ExitCode.Success)
//  }
//
//}
