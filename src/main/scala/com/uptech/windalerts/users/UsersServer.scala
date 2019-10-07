package com.uptech.windalerts.users

import java.io.FileInputStream
import java.util.concurrent.TimeUnit

import cats.data.EitherT
import cats.effect.{IO, _}
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.{HttpErrorHandler, domain}
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import scala.util.{Random, Try}

object UsersServer extends IOApp {
  private val logger = getLogger
  private val REFRESH_TOKEN_EXPIRY = 7L * 24L * 60L * 60L * 1000L


  logger.error("Starting")

  val dbIO = for {
    credentials <- IO(Try(GoogleCredentials.fromStream(new FileInputStream("/app/resources/wind-alerts-staging.json")))
      .getOrElse(GoogleCredentials.getApplicationDefault))
    options <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId("wind-alerts-staging").build)
    _ <- IO(FirebaseApp.initializeApp(options))
    db <- IO(FirestoreClient.getFirestore)
  } yield db

  val db = dbIO.unsafeRunSync()

  val credentialsRepositoryAlgebra = new FirestoreCredentialsRepository(db)

  private def signupEndpoints(
                               userService: UserService,
                               httpErrorHandler: HttpErrorHandler[IO],
                               refreshTokenRepositoryAlgebra: RefreshTokenRepositoryAlgebra
                             ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case req@POST -> Root =>
        val action = for {
          rr <- req.as[RegisterRequest]
          result <- userService.createUser(rr).value
        } yield result
        action.flatMap {
          case Right(saved) => Ok(saved)
          case Left(error) => httpErrorHandler.handleError(error)
        }

      case req@POST -> Root / "login" =>
        val action = for {
          credentials <- EitherT.liftF(req.as[Credentials])
          dbCredentials <- userService.getByCredentials(credentials)
          refreshToken <- EitherT.liftF(refreshTokenRepositoryAlgebra.create(RefreshToken(generateRefreshToken(40), (System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY), dbCredentials.id.get)))
          token <- createToken(dbCredentials.id.get, 60)
          tokens <- tokens(token.accessToken, refreshToken, token.expiredAt)
        } yield tokens

        action.value.flatMap {
          case Right(tokens) => Ok(tokens)
          case Left(error) => httpErrorHandler.handleError(error)
        }

      case req@POST -> Root / "refresh" =>
        val action = for {
          refreshToken <- EitherT.liftF(req.as[AccessTokenRequest])
          dbRefreshToken <- refreshTokenRepositoryAlgebra.getByRefreshToken(refreshToken.refreshToken).toRight(RefreshTokenNotFoundError())
          validRefreshToken <- {
            val eitherT: EitherT[IO, RefreshTokenExpiredError, RefreshToken] = EitherT.fromEither {
              if (dbRefreshToken.isExpired()) {
                Left(RefreshTokenExpiredError())
              } else {
                Right(dbRefreshToken)
              }
            }
            eitherT
          }

          token <- createToken(validRefreshToken.userId, 60)
          tokens <- tokens(token.accessToken, dbRefreshToken, token.expiredAt)
        } yield tokens

        action.value.flatMap {
          case Right(tokens) => Ok(tokens)
          case Left(error) => httpErrorHandler.handleError(error)
        }
    }

  def tokens(accessToken: String, refreshToken: RefreshToken, expiredAt:Long): EitherT[IO, ValidationError, Tokens] = {
    val tokens = domain.Tokens(accessToken, refreshToken.refreshToken, expiredAt)
    EitherT.right(IO(tokens))
  }

  private def generateRefreshToken(n: Int) = {
    val alpha = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val size = alpha.size

    (1 to n).map(_ => alpha(Random.nextInt.abs % size)).mkString
  }

  def createToken(userId: String, expirationInMinutes: Int): EitherT[IO, ValidationError, AccessTokenWithExpiry] = {
    val current = System.currentTimeMillis()
    val expiry = current / 1000 + TimeUnit.MINUTES.toSeconds(expirationInMinutes)
    val claims = JwtClaim(
      expiration = Some(expiry),
      issuedAt = Some(current / 1000),
      issuer = Some("wind-alerts.com"),
      subject = Some(userId)
    )

    EitherT.right(IO(AccessTokenWithExpiry(Jwt.encode(claims, "secretKey", JwtAlgorithm.HS256), expiry)))
  }

  case class AccessTokenWithExpiry(accessToken:String, expiredAt:Long)


  private val service = new UserService(new FirestoreUserRepository(db), new FirestoreCredentialsRepository(db))
  val httpApp = Router(
    "/v1/users" -> signupEndpoints(service, new HttpErrorHandler[IO](), new FirestoreRefreshTokenRepository(db))
  ).orNotFound

  override def run(args: List[String]): IO[ExitCode] = {
    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }
}
