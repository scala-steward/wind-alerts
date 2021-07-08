package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.effect.{Async, ContextShift, IO}
import cats.implicits._
import com.uptech.windalerts.core.AlertNotFoundError
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.alerts.{Alerts, AlertsRepository}
import com.uptech.windalerts.infrastructure.endpoints.dtos._
import io.scalaland.chimney.dsl._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal

import scala.concurrent.ExecutionContext.Implicits.global

class MongoAlertsRepository[F[_]](collection: MongoCollection[Alert])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends AlertsRepository[F] {

  override def disableAllButOneAlerts(userId: String): F[Seq[Alert]] = {
    for {
      all <- getAllForUser(userId)
      updatedIOs <- all.alerts.sortBy(_.createdAt).drop(1).map(alert => update(alert._id.toHexString, alert.copy(enabled = false))).toList.sequence
    } yield updatedIOs
  }

  private def update(alertId: String, alert: Alert): F[Alert] = {
    Async.fromFuture(M.pure(collection.replaceOne(equal("_id", new ObjectId(alertId)), alert).toFuture().map(_ => alert)))
  }

  def getById(id: String): OptionT[F, Alert] = {
    OptionT(Async.fromFuture(M.pure(collection.find(equal("_id", new ObjectId(id))).toFuture().map(_.headOption))))
  }

  override def getAllEnabled(): F[Seq[Alert]] = {
    Async.fromFuture(M.pure(collection.find(equal("enabled", true)).toFuture()))
  }

  override def save(alertRequest: AlertRequest, user: String): F[Alert] = {
    val alert = Alert(alertRequest, user)

    Async.fromFuture(M.pure(collection.insertOne(alert).toFuture().map(_ => alert)))
  }

  override def delete(requester: String, alertId: String): EitherT[F, AlertNotFoundError, Unit] = {
    for {
      _ <- getById(alertId).toRight(AlertNotFoundError())
      deleted <- EitherT.right(Async.fromFuture(M.pure(collection.deleteOne(equal("_id", new ObjectId(alertId))).toFuture().map(_ => ()))))
    } yield deleted
  }

  override def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, AlertNotFoundError, Alert] = {
    for {
      oldAlert <- getById(alertId).toRight(AlertNotFoundError())
      alertUpdated = updateAlertRequest.into[Alert]
        .withFieldComputed(_._id, u => new ObjectId(alertId))
        .withFieldComputed(_.owner, _ => requester)
        .withFieldComputed(_.createdAt, _ => oldAlert.createdAt).transform
      _ <- EitherT.liftF(M.pure(collection.replaceOne(equal("_id", new ObjectId(alertId)), alertUpdated).toFuture()))
      alert <- getById(alertId).toRight(AlertNotFoundError())
    } yield alert
  }

  override def getAllForUser(user: String): F[Alerts] = {
    findByCriteria(equal("owner", user)).map(Alerts(_))
  }

  private def findByCriteria(criteria: Bson) =
    Async.fromFuture(M.pure(collection.find(criteria).toFuture()))

}