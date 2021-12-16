package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.effect.{Async, ContextShift}
import cats.implicits._
import com.uptech.windalerts.core.AlertNotFoundError
import com.uptech.windalerts.core.alerts.{AlertsRepository, TimeRange}
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.infrastructure.endpoints.dtos._
import io.scalaland.chimney.dsl._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}

import scala.concurrent.ExecutionContext.Implicits.global

class MongoAlertsRepository[F[_]](collection: MongoCollection[DBAlert])(implicit cs: ContextShift[F], s: Async[F], M: Monad[F]) extends AlertsRepository[F] {

  override def disableAllButFirstAlerts(userId: String): F[Unit] = {
    for {
      all <- getAllForUser(userId)
      _ <- all.sortBy(_.createdAt).tail.map(alert => update(alert.id, alert.copy(enabled = false))).toList.sequence
    } yield ()
  }

  override def getFirstAlert(userId: String): OptionT[F, Alert] = {
    OptionT(for {
      all <- getAllForUser(userId)
      head = all.sortBy(_.createdAt).headOption
    } yield head)
  }

  private def update(alertId: String, alert: Alert): F[Alert] = {
    Async.fromFuture(M.pure(collection.replaceOne(equal("_id", new ObjectId(alertId)), DBAlert(alert)).toFuture().map(_ => alert)))
  }

  def getById(id: String) = {
    OptionT(Async.fromFuture(M.pure(collection.find(equal("_id", new ObjectId(id))).toFuture().map(_.headOption.map(_.toAlert())))))
  }

  override def getAllEnabled(): F[Seq[Alert]] = {
    Async.fromFuture(M.pure(collection.find(equal("enabled", true)).toFuture())).map(_.map(_.toAlert()))
  }

  override def save(alertRequest: AlertRequest, user: String): F[Alert] = {
    val dBAlert = DBAlert(alertRequest, user)

    Async.fromFuture(M.pure(collection.insertOne(dBAlert).toFuture().map(_ => dBAlert.toAlert())))
  }

  override def delete(requester: String, alertId: String): EitherT[F, AlertNotFoundError, Unit] = {
    for {
      dbAlert <- getById(alertId).toRight(AlertNotFoundError())
      _ <- EitherT.cond[F](dbAlert.owner == requester, (), AlertNotFoundError())
      deleted <- EitherT.right(Async.fromFuture(M.pure(collection.deleteOne(equal("_id", new ObjectId(alertId))).toFuture().map(_ => ()))))
    } yield deleted
  }

  override def update(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, AlertNotFoundError, Alert] = {
    for {
      oldAlert <- getById(alertId).toRight(AlertNotFoundError())
      alertUpdated = DBAlert(updateAlertRequest, requester, alertId, oldAlert.createdAt)
      _ <- EitherT.liftF(M.pure(collection.replaceOne(equal("_id", new ObjectId(alertId)), alertUpdated).toFuture()))
      alert <- getById(alertId).toRight(AlertNotFoundError())
    } yield alert
  }

  override def getAllForUser(user: String): F[Seq[Alert]] = {
    findByCriteria(equal("owner", user))
  }

  override def getAllEnabledForUser(user: String): F[Seq[Alert]] = {
    findByCriteria(
      and(equal("owner", user),
        equal("enabled", true),
      ))
  }

  private def findByCriteria(criteria: Bson) =
    Async.fromFuture(M.pure(collection.find(criteria).toFuture())).map(_.map(_.toAlert()))

}

case class DBAlert(
                    _id: ObjectId,
                    owner: String,
                    beachId: Long,
                    days: Seq[Long],
                    swellDirections: Seq[String],
                    timeRanges: Seq[TimeRange],
                    waveHeightFrom: Double,
                    waveHeightTo: Double,
                    windDirections: Seq[String],
                    tideHeightStatuses: Seq[String] = Seq("Rising", "Falling"),
                    enabled: Boolean,
                    timeZone: String = "Australia/Sydney",
                    createdAt: Long) {

  def toAlert(): Alert = {
    this.into[Alert]
      .withFieldComputed(_.id, dbAlert => dbAlert._id.toHexString)
      .transform
  }
}

object DBAlert {
  def apply(alert: Alert): DBAlert = {
    alert.into[DBAlert]
      .withFieldComputed(_._id, alert => new ObjectId(alert.id))
      .transform
  }

  def apply(alertRequest: AlertRequest, user: String): DBAlert = {
    alertRequest.into[DBAlert]
      .withFieldComputed(_.owner, _ => user)
      .withFieldComputed(_._id, _ => new ObjectId())
      .withFieldComputed(_.createdAt, _ => System.currentTimeMillis())
      .transform
  }

  def apply(alertRequest: AlertRequest, user: String, alertId:String, createdAt:Long): DBAlert = {
    alertRequest.into[DBAlert]
      .withFieldComputed(_.owner, _ => user)
      .withFieldComputed(_._id, _ => new ObjectId(alertId))
      .withFieldComputed(_.createdAt, _ => createdAt)
      .transform
  }
}