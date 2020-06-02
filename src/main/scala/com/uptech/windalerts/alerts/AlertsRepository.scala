package com.uptech.windalerts.alerts

import cats.data.{EitherT, OptionT}
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.{AlertNotFoundError, ValidationError, conversions, domain}
import io.scalaland.chimney.dsl._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}

import scala.concurrent.ExecutionContext.Implicits.global

trait AlertsRepositoryT[F[_]] {
  def disableAllButOneAlerts(userId: String): F[Seq[AlertT]]

  def getById(id: String): F[Option[AlertT]]

  def getAllForDay(day: Int, p:AlertT=>Boolean): F[Seq[AlertT]]

  def getAllForUser(user: String): F[domain.AlertsT]

  def save(alert: AlertRequest, user: String): F[AlertT]

  def delete(requester: String, id: String): EitherT[F, ValidationError, Unit]

  def updateT(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, ValidationError, AlertT]
}

class MongoAlertsRepositoryAlgebra(collection: MongoCollection[AlertT])(implicit cs: ContextShift[IO]) extends AlertsRepositoryT[IO] {


  private def findByCriteria(criteria: Bson) =
    IO.fromFuture(IO(collection.find(criteria).toFuture()))

  override def disableAllButOneAlerts(userId: String): IO[Seq[AlertT]] = {
    for {
      all <- getAllForUser(userId)
      updatedIOs <- IO({
        all.alerts.filter(_.enabled) match {
          case Seq() => List[IO[AlertT]]()
          case Seq(only) => List[IO[AlertT]](IO(only))
          case longSeq => longSeq.tail.map(alert => update(alert._id.toHexString, alert.copy(enabled = false)))
        }
      }
      )
      updatedAlerts <- conversions.toIOSeq(updatedIOs)
    } yield updatedAlerts
  }

  private def update(alertId: String, alert: AlertT): IO[AlertT] = {
    IO.fromFuture(IO(collection.replaceOne(equal("_id", new ObjectId(alertId)), alert).toFuture().map(_ => alert)))
  }

  override def getById(id: String): IO[Option[AlertT]] = {
    findByCriteria(equal("_id", new ObjectId(id))).map(_.headOption)
  }

  override def getAllForDay(day: Int, p:AlertT=>Boolean): IO[Seq[AlertT]] = {
    findByCriteria(and(equal("days", day), equal("enabled", true))).map(s=>s.filter(p))
  }

  override def getAllForUser(user: String): IO[AlertsT] = {
    findByCriteria(equal("owner", user)).map(AlertsT(_))
  }

  override def save(alertRequest: AlertRequest, user: String): IO[AlertT] = {
    val alert = AlertT(alertRequest, user)

    IO.fromFuture(IO(collection.insertOne(alert).toFuture().map(_ => alert)))
  }

  override def delete(requester: String, alertId: String): EitherT[IO, ValidationError, Unit] = {
    EitherT.liftF(IO.fromFuture(IO(collection.deleteOne(equal("_id", new ObjectId(alertId))).toFuture().map(_=>()))))
  }

  override def updateT(requester: String, alertId: String, updateAlertRequest: AlertRequest):EitherT[IO, ValidationError, AlertT] = {
    val alertUpdated = updateAlertRequest.into[AlertT].withFieldComputed(_._id, u => new ObjectId(alertId)).withFieldComputed(_.owner, _ => requester).transform
    for {
      updated <- EitherT.liftF(IO(collection.replaceOne(equal("_id", new ObjectId(alertId)), alertUpdated).toFuture()))
      alert <- OptionT(getById(alertId)).toRight(AlertNotFoundError())
    } yield alert
  }

}