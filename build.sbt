
ThisBuild / organization := "com.uptech"
ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.8"
lazy val root = (project in file("."))
  .settings(
    Compile / mainClass := Some("com.uptech.windalerts.BeachesServer"),
    name := "wind-alerts",
    libraryDependencies ++= List(
      "org.scala-lang" % "scala-library" % props.scalaVersion,
      "org.http4s" % "http4s-blaze-server_2.13" % props.http4sVersion,
      "org.http4s" % "http4s-blaze-client_2.13" % props.http4sVersion,
      "org.http4s" % "http4s-dsl_2.13" % props.http4sVersion,
      "io.circe" % "circe-config_2.13" % props.circeConfigVersion,
      "io.circe" % "circe-core_2.13" % props.circeVersion,
      "io.circe" % "circe-parser_2.13" % props.circeVersion,
      "io.circe" % "circe-generic_2.13" % props.circeVersion,
      "io.circe" % "circe-optics_2.13" % props.circeVersion,
      "io.circe" % "circe-generic-extras_2.13" % props.circeGenericExtrasVersion,
      "org.http4s" % "http4s-circe_2.13" % props.http4sVersion,
      "org.typelevel" % "cats-core_2.13" % props.catsVersion,
      "com.softwaremill.sttp" % "core_2.13" % "1.7.2",
      "org.webjars" % "webjars-locator" % "0.45",
      "org.webjars" % "swagger-ui" % "3.52.5",
      "io.scalaland" % "chimney_2.13" % "0.6.1",
      "com.google.oauth-client" % "google-oauth-client-java6" % "1.33.3",
      "com.google.oauth-client" % "google-oauth-client-jetty" % "1.33.3",
      "com.google.apis" % "google-api-services-androidpublisher" % "v3-rev20220411-1.32.1",
      "org.mongodb.scala" % "mongo-scala-driver_2.13" % "2.9.0",
      "io.netty" % "netty-all" % "4.1.76.Final",
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "com.google.firebase" % "firebase-admin" % "6.16.0",
      "javax.mail" % "mail" % "1.5.0-b01",
      "com.github.t3hnar" % "scala-bcrypt_2.13" % "4.3.0",
      "com.turo" % "pushy" % "0.13.10",
      "junit" % "junit" % "4.13.2" % Test,
      "org.scalatest" % "scalatest_2.13" % "3.3.0-SNAP3" % Test,
      "org.specs2" % "specs2-junit_2.13" % "4.15.0" % Test,
      "com.pauldijou" % "jwt-core_2.13" % "5.0.0",
      "dev.profunktor" % "http4s-jwt-auth_2.13" % "1.0.0-RC2",
      "com.restfb" % "restfb" % "2022.4.0",
      "io.github.resilience4j" % "resilience4j-circuitbreaker" % "1.7.1",
      "io.github.resilience4j" % "resilience4j-bulkhead" % "1.7.1",
      "io.github.resilience4j" % "resilience4j-retry" % "1.7.1",
      "io.github.resilience4j" % "resilience4j-all" % "1.7.1",
      "com.google.cloud" % "google-cloud-pubsub" % "1.116.4",
      "net.logstash.logback" % "logstash-logback-encoder" % "6.6",
      "org.scalacheck" % "scalacheck_2.13" % "1.16.0",
      "org.scalatest" % "scalatest_2.13" % "3.2.11",
      "org.scalatestplus" % "scalacheck-1-14_2.13" % "3.2.2.0",
      "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "3.4.5"
    )
  ).enablePlugins(JavaServerAppPackaging, DockerPlugin)


lazy val props = new {
  val mavenCompilerSource = "1.8"
  val mavenCompilerTarget = "1.8"
  val encoding = "UTF-8"
  val scalaVersion = "2.13.8"
  val scalaCompatVersion = "2.13"
  val spec2Version = "4.2.0"
  val envProjectId = "wind-alerts-staging"
  val http4sVersion = "0.22.1"
  val catsVersion = "2.7.0"
  val circeVersion = "0.14.1"
  val circeGenericVersion = "0.14.1"
  val circeGenericExtrasVersion = "0.14.1"
  val circeConfigVersion = "0.8.0"
}

lazy val libs = new {
  val librariesBom = "com.google.cloud" % "libraries-bom" % "22.0.0"
}
