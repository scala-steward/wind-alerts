//package com.uptech.windalerts.users
//
//import java.time.Instant
//import java.util.concurrent.TimeUnit
//
//import cats.effect.IO
//import cats.Id
//import com.uptech.windalerts.domain.Domain
//import com.uptech.windalerts.domain.Domain.{RegisterRequest, User}
//import org.http4s.{HttpRoutes, HttpService}
//import org.http4s.dsl.io._
//
//import com.uptech.windalerts.domain.DomainCodec._
//import org.http4s.dsl.impl.Root
//import org.http4s.headers.Authorization
//import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtSprayJson}
//
//import scala.concurrent.duration._
//
//object jwtStatefulExample {
//
//
//  import ExampleAuthHelpers._
//
//  val jwtStore =
//    dummyBackingStore[IO, SecureRandomId, AugmentedJWT[HMACSHA256, String]](s => SecureRandomId.coerce(s.id))
//
//  //We create a way to store our users. You can attach this to say, your doobie accessor
//  val userStore: BackingStore[IO, String, User] = dummyBackingStore[IO, String, User](_.email)
//
//  //Our signing key. Instantiate in a safe way using .generateKey[F]
//  val signingKey: MacSigningKey[HMACSHA256] = HMACSHA256.generateKey[Id]
//
//  val jwtStatefulAuth =
//    JWTAuthenticator.backed.inBearerToken(
//      expiryDuration = 10.minutes, //Absolute expiration time
//      maxIdle        = None,
//      tokenStore     = jwtStore,
//      identityStore  = userStore,
//      signingKey     = signingKey
//    )
//
//  val Auth =
//    SecuredRequestHandler(jwtStatefulAuth)
//
//  val impureClaims = JWTClaims(expiration = Some(Instant.now.plusSeconds(10.minutes.toSeconds)))
//
//  val jwt: Either[Throwable, JWTMac[HMACSHA256]] = for {
//    key             <- HMACSHA256.generateKey[MacErrorM]
//    jwt             <- JWTMacImpure.build[HMACSHA256](impureClaims, key) //You can sign and build a jwt object directly
//    verifiedFromObj <- JWTMacImpure.verifyFromInstance[HMACSHA256](jwt, key)
//    stringjwt       <- JWTMacImpure.buildToString[HMACSHA256](impureClaims, key) //Or build it straight to string
//    isverified      <- JWTMacImpure.verifyFromString[HMACSHA256](stringjwt, key) //You can verify straight from a string
//    parsed          <- JWTMacImpure.verifyAndParse[HMACSHA256](stringjwt, key) //Or verify and return the actual instance
//  } yield parsed
//
//  val algorithm = JwtAlgorithm.HS256
//  val secretKey = "rockthejvmsecret"
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
//
//  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
//
//    //Where user is the case class User above
//    case req@POST -> Root / "register" => {
//      req.decode[RegisterRequest] { registerRequest =>
//        Ok(createToken(registerRequest.email, 1))
//        //            val x = jwt.map(j=>
//        //              AugmentedJWT[HMACSHA256, String](SecureRandomId.Strong.generate, j.a , registerRequest.email, Instant.now().plusMillis(settings.expiryDuration._1), None)
//        //            )
//        //
//        //            for {
//        //              user <- IO.fromEither(x)
//        //              u1 <- jwtStore.put(x.right.get)
//        //              u2 <- userStore.put(User(registerRequest.email, registerRequest.email))
//        //              u3 <- IO(println(u1 + " " + u2))
//        //              response <- Ok(IO.pure(user.id.toString) + "   id   "+ user.jwt.id.get)
//        //            } yield response
//
//      }
//    }
//
//    case req@GET -> Root / "users" / "devices" =>
//      val alert = for {
//        header <- IO.fromEither(req.headers.get(Authorization).toRight(new RuntimeException("Couldn't find an Authorization header")))
//        u <-  IO(JwtSprayJson.isValid(header.value.replaceFirst("Bearer ", ""), secretKey, Seq(algorithm)))
//      } yield (u.booleanValue())
//      Ok("" + alert.unsafeRunSync())
//  }
//
//  /*
//  Now from here, if want want to create services, we simply use the following
//  (Note: Since the type of the service is HttpService[IO], we can mount it like any other endpoint!):
//   */
//  val service: HttpRoutes[IO] = Auth.liftService(TSecAuthService {
//    //Where user is the case class User above
//    case request@GET -> Root / "api" asAuthed user =>
//      /*
//      Note: The request is of type: SecuredRequest, which carries:
//      1. The request
//      2. The Authenticator (i.e token)
//      3. The identity (i.e in this case, User)
//       */
//      val r: SecuredRequest[IO, User, AugmentedJWT[HMACSHA256, String]] = request
//      Ok()
//  })
//
//}