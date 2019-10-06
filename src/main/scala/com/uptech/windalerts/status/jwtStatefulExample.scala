//package com.uptech.windalerts.status
//
//import java.util
//import java.util.concurrent.TimeUnit
//
//import cats.data.{EitherT, OptionT}
//import cats.effect.IO
//import cats.implicits._
//import com.google.cloud.firestore
//import com.google.cloud.firestore.Firestore
//import com.uptech.windalerts.domain.domain.{Credentials, User}
//import com.uptech.windalerts.domain.Errors.{InvalidCredentials, UserAlreadyRegistered}
//import com.uptech.windalerts.domain.HttpErrorHandler
//import com.uptech.windalerts.users.UsersRepository.UserBean
//import org.http4s.HttpRoutes
//import org.http4s.dsl.impl.Root
//import org.http4s.dsl.io._
//import org.http4s.headers.Authorization
//import pdi.jwt.{JwtAlgorithm, JwtClaim}
//
//import scala.collection.JavaConverters
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.Future
//import scala.util.{Failure, Success}
//import com.uptech.windalerts.domain.codecs._
//
//class jwtStatefulExample(db: Firestore, H: HttpErrorHandler[IO]) {
//  val users = db.collection("users-jwt")
//
//  val algorithm = JwtAlgorithm.HS256
//  val secretKey = "rockthejvmsecret"
//
//  def isValid(header: Authorization.HeaderT) = {
//    JwtSprayJson.decode(header.value.replaceFirst("Bearer ", ""), secretKey, Seq(algorithm)) match {
//      case Success(claims) => {
//        claims.expiration.getOrElse(0L) < System.currentTimeMillis() / 1000
//        println(s"claims ${claims.subject}")
//        true
//      }
//      case Failure(_) => false
//    }
//
//  }
//
//  def createToken(username: String, expirationPeriodInDays: Int): String = {
//    val claims = JwtClaim(
//      expiration = Some(System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(expirationPeriodInDays)),
//      issuedAt = Some(System.currentTimeMillis() / 1000),
//      issuer = Some("rockthejvm.com"),
//      subject = Some(username)
//    )
//
//    JwtSprayJson.encode(claims, secretKey, algorithm) // JWT string
//  }
//
//
//  def routes(): HttpRoutes[IO] = HttpRoutes.of[IO] {
//
//    case req@POST -> Root / "register" => {
//      req.decode[Credentials] { credentials => {
//        val resFut = IO.fromFuture(IO(registerIfNotRegistered(credentials).value))
//
//        resFut.unsafeRunSync() match {
//          case Left(error) => H.handleThrowable(error)
//          case Right(value) => Ok(value)
//        }
//      }
//      }
//    }
//
//    case req@POST -> Root / "login" => {
//      req.decode[Credentials] { credentials => {
//        val resFut = for {
//          x <- EitherT(findByCredentials(credentials))
//          y <- EitherT(Future(Either.right[Exception, String](createToken(x, 10))))
//        } yield (y)
//        IO.fromFuture(IO(resFut.value)).unsafeRunSync()
//        match {
//          case Left(error) => H.handleThrowable(error)
//          case Right(value) => Ok(value)
//        }
//
//      }
//      }
//    }
//
//    case req@GET -> Root / "users" / "devices" =>
//      val alert = for {
//        header <- IO.fromEither(req.headers.get(Authorization).toRight(new RuntimeException("Couldn't find an Authorization header")))
//        u <- IO(isValid(header))
//      } yield (u.booleanValue())
//      Ok("" + alert.unsafeRunSync())
//  }
//
//
//  private def registerIfNotRegistered(credentials: Credentials) = {
//    for {
//      x <- EitherT(checkIfAlreadyRegistered(credentials))
//      y <- EitherT(register(x))
//    } yield y
//  }
//
//
//  private def findByCredentials(credentials: Credentials) = {
//    val futureDocuments =
//      j2s(
//        users.whereEqualTo("email", credentials.email)
//              .whereEqualTo("deviceType", credentials.deviceType)
//              .whereEqualTo("password", credentials.password).get())
//      .map(res => {
//        j2s(res.getDocuments).map(document => {
//          document.getId
//      })
//    })
//
//    futureDocuments.map(res => {
//      if (res.isEmpty) {
//        Either.left(InvalidCredentials(s"Invalid credentials ${credentials.email}"))
//      } else {
//        Either.right(res.head)
//      }
//    })
//  }
//
//  private def checkIfAlreadyRegistered(credentials: Credentials) = {
//    val futureResult = j2s(users.whereEqualTo("email", credentials.email).whereEqualTo("deviceType", credentials.deviceType).get())
//    val futureDocuments = futureResult.map(res => {
//      j2s(res.getDocuments).map(document => {
//        document.getId
//      })
//    })
//
//    futureDocuments.map(res => {
//      if (res.isEmpty) {
//        Either.right(credentials)
//      } else {
//        Either.left(UserAlreadyRegistered(s"User ${credentials.email} already registered on ${credentials.deviceType}"))
//      }
//    })
//  }
//
//  private def register(registerRequest: Credentials) = {
//    j2s(users.add(new UserBean(registerRequest.email, registerRequest.password, registerRequest.deviceType))).map(doc => {
//      try {
//        Either.right(doc.getId)
//      } catch {
//        case e: Exception => Either.left(e)
//      }
//    })
//  }
//
//
//  def j2s[A](inputList: util.List[A]) = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq
//
//  def j2s[K, V](map: util.Map[K, V]) = JavaConverters.mapAsScalaMap(map).toMap
//
//  def j2s[A](javaFuture: util.concurrent.Future[A]): Future[A] = {
//    Future(javaFuture.get())
//  }
//
//
//}