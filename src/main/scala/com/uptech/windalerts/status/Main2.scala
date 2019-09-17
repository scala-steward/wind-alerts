package com.uptech.windalerts.status

import java.io.FileInputStream
import java.util

import cats.effect.IO
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import com.jmethods.catatumbo.EntityManager
import com.jmethods.catatumbo.EntityManagerFactory
import com.uptech.windalerts.domain.Domain.{Alert, AlertBean, TimeRange}
import io.circe.Json
import io.circe.syntax._

import scala.util.Try

object Main2 extends App {
  import com.uptech.windalerts.domain.DomainCodec._
  val res = for {
    credentials <- IO(Try(GoogleCredentials.fromStream(new FileInputStream("/app/resources/wind-alerts-staging.json")))
      .getOrElse(GoogleCredentials.getApplicationDefault))
    options <- IO(new FirebaseOptions.Builder().setCredentials(credentials).setProjectId("wind-alerts-staging").build)
    _ <- IO(FirebaseApp.initializeApp(options))
    db <- IO(FirestoreClient.getFirestore)
    auth <- IO(FirebaseAuth.getInstance)

    emf <- IO(EntityManagerFactory.getInstance)
    em <- IO(emf.createDefaultEntityManager())
    req <- IO(em.createEntityQueryRequest("SELECT * FROM alerts"))
    res <- IO(em.executeEntityQueryRequest(classOf[AlertBean], req).getResults)

  } yield (res)

  println(res.unsafeRunSync())

}
