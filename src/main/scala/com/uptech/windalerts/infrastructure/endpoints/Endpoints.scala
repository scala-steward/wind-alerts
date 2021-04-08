package com.uptech.windalerts.infrastructure.endpoints
import cats.effect.Effect
import com.sun.tools.internal.ws.wscompile.AuthInfo
import org.http4s.circe.jsonOf
import org.http4s.rho.RhoRoutes

class Endpoints[F[+_] : Effect] {

  val api = new RhoRoutes[F] {
    GET / "somePath" / pathVar[Int]("someInt", "parameter description") +? paramD[String]("name", "parameter description") |>> {
      (someInt: Int, name: String) => Ok("result")
    }
  }

  object Auth extends org.http4s.rho.AuthedContext[F, AuthInfo]
  val api2 = new RhoRoutes[F] {
    import org.http4s.rho.swagger.syntax.io._

    "Description of api endpoint" **      // Description is optional and specific to Swagger
      POST / "somePath" / pathVar[Int]("someInt", "parameter description") >>> Auth.auth() ^ jsonOf[F, T] |>> {
      (someInt: Int, au: AuthInfo, body: T) => Ok("result")
    }
  }
}
