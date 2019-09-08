package com.uptech.windalerts.alerts

import cats.effect.{IO, _}
import cats.implicits._
import com.uptech.windalerts.domain.Domain
import com.uptech.windalerts.domain.Domain.BeachId
import com.uptech.windalerts.status.{Beaches, Swells, Tides, Winds}
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.log4s.getLogger

object Main extends IOApp {

  private val logger = getLogger

  val beaches = Beaches.ServiceImpl(Winds.impl, Swells.impl, Tides.impl)
  val alerts = Alerts.FireStoreBackedService

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(sys.env("PORT").toInt, "0.0.0.0")
      .withHttpApp(sendAlertsRoute(alerts, beaches))
      .serve
      .compile
      .drain
      .as(ExitCode.Success)

  def sendAlertsRoute(A: Alerts.Service, B: Beaches.Service) = HttpRoutes.of[IO] {

    case GET -> Root / "notify" => {
      val usersToBeNotified = for {
        alerts              <- A.getAllForDay
        alertsByBeaches     <- IO(alerts.groupBy(_.beachId).map(
                                    kv => {
                                      (B.get(BeachId(kv._1)), kv._2)
                                    }))

        asIOMap             <- toIOMap(alertsByBeaches)
        alertsToBeNotified  <- IO(asIOMap.map(kv => (kv._1, kv._2.filter(_.isToBeNotified(kv._1)))))
        usersToBeNotified   <- IO(alertsToBeNotified.values.flatMap(elem=>elem).map(_.owner).toSeq)
        printIO             <- IO(logger.info("" + usersToBeNotified))
      } yield printIO
      Ok(usersToBeNotified)
    }

  }.orNotFound


  private def toIOMap(m: Map[IO[Domain.Beach], Seq[Domain.Alert]]) = {
    val a: List[(IO[Domain.Beach], Seq[Domain.Alert])] = m.toList
    a.traverse {
      case (io, s) => io.map(s2 => (s2, s))
    }.map {
      _.toMap
    }
  }


}