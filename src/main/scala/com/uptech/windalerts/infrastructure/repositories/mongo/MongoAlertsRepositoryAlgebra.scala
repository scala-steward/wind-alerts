package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.data.{EitherT, OptionT}
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.core.alerts.domain.AlertT
import com.uptech.windalerts.core.alerts.{AlertsRepositoryT, AlertsT}
import com.uptech.windalerts.domain.AlertNotFoundError
import com.uptech.windalerts.domain.domain._
import io.scalaland.chimney.dsl._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}

import scala.concurrent.ExecutionContext.Implicits.global

class MongoAlertsRepositoryAlgebra(collection: MongoCollection[AlertT])(implicit cs: ContextShift[IO]) extends AlertsRepositoryT[IO] {


  private def findByCriteria(criteria: Bson) =
    IO.fromFuture(IO(collection.find(criteria).toFuture()))

  override def disableAllButOneAlerts(userId: String): IO[Seq[AlertT]] = {
    for {
      all <- getAllForUser(userId)
      updatedIOs <- IO({
        all.alerts.sortBy(_.createdAt).drop(1).map(alert => update(alert._id.toHexString, alert.copy(enabled = false)))
      }
      )
      updatedAlerts <- toIOSeq(updatedIOs)
    } yield updatedAlerts
  }

  private def update(alertId: String, alert: AlertT): IO[AlertT] = {
    IO.fromFuture(IO(collection.replaceOne(equal("_id", new ObjectId(alertId)), alert).toFuture().map(_ => alert)))
  }

  def getById(id: String): OptionT[IO, AlertT] = {
    OptionT(findByCriteria(equal("_id", new ObjectId(id))).map(_.headOption))
  }

  override def getAllForUser(user: String): IO[AlertsT] = {
    findByCriteria(equal("owner", user)).map(AlertsT(_))
  }

  override def getAllEnabled(): IO[Seq[AlertT]]  = {
    findByCriteria( equal("enabled", true))
  }

  override def save(alertRequest: AlertRequest, user: String): IO[AlertT] = {
    val alert = AlertT(alertRequest, user)

    IO.fromFuture(IO(collection.insertOne(alert).toFuture().map(_ => alert)))
  }

  override def delete(requester: String, alertId: String): EitherT[IO, AlertNotFoundError, Unit] = {
    for {
      _ <- getById(alertId).toRight(AlertNotFoundError())
      deleted <- EitherT.right(IO.fromFuture(IO(collection.deleteOne(equal("_id", new ObjectId(alertId))).toFuture().map(_=>()))))
    } yield deleted
  }

  override def update(requester: String, alertId: String, updateAlertRequest: AlertRequest):EitherT[IO, AlertNotFoundError, AlertT] = {
    for {
      oldAlert <- getById(alertId).toRight(AlertNotFoundError())
      alertUpdated = updateAlertRequest.into[AlertT]
                        .withFieldComputed(_._id, u => new ObjectId(alertId))
                        .withFieldComputed(_.owner, _ => requester)
                        .withFieldComputed(_.createdAt, _=> oldAlert.createdAt).transform
      _ <- EitherT.liftF(IO(collection.replaceOne(equal("_id", new ObjectId(alertId)), alertUpdated).toFuture()))
      alert <- getById(alertId).toRight(AlertNotFoundError())
    } yield alert
  }


  def toIO[T](x: List[IO[T]]): IO[List[T]] = {
    import cats.implicits._
    x.sequence
  }

  def toIOSeq[T](x: Seq[IO[T]]):IO[Seq[T]] = {
    toIO(x.toList).map(_.toSeq)
  }
}