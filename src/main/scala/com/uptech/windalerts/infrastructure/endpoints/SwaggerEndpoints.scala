package com.uptech.windalerts.infrastructure.endpoints

import cats.effect.{Blocker, Effect, _}
import io.circe.Json
import org.http4s.Uri.uri
import org.http4s.circe.jsonEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.{HttpRoutes, StaticFile}
import org.webjars.WebJarAssetLocator

class SwaggerEndpoints[F[_] : Effect] extends Http4sDsl[F] {
  private val swaggerUiPath = Path.fromString("swagger-ui")
  private val swaggerUiResources = s"/META-INF/resources/webjars/swagger-ui/$swaggerUiVersion"
  private lazy val swaggerUiVersion: String = {
    Option(new WebJarAssetLocator().getWebJars.get("swagger-ui")).fold {
      throw new RuntimeException(s"Could not detect swagger-ui webjar version")
    } { version =>
      version
    }
  }

  def endpoints(blocker: Blocker)(implicit cs: ContextShift[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case request@GET -> Root / "swagger.yaml" =>
        StaticFile.fromResource("swagger.yaml", blocker, Some(request)).getOrElseF(NotFound())
      case request@GET -> `swaggerUiPath` / "config.json" =>
        //Specifies Swagger spec URL
        Ok(Json.obj("url" -> Json.fromString(s"/swagger.yaml")))//Entry point to Swagger UI
      case request@GET -> `swaggerUiPath` =>
        PermanentRedirect(Location(uri("swagger-ui/index.html")))
      case request@GET -> path if path.startsWith(swaggerUiPath) =>
        //Serves Swagger UI files

        val file = "/" + path.segments.drop(swaggerUiPath.segments.size).mkString("/")
        (if (file == "/index.html") {
          StaticFile.fromResource("/swagger-ui/index.html", blocker, Some(request))
        } else {
          StaticFile.fromResource(swaggerUiResources + file, blocker, Some(request))
        }).getOrElseF(NotFound())

    }

}
