package com.uptech.windalerts.infrastructure.repositories.mongo


import cats.data.OptionT
import cats.effect.{Async, ContextShift}
import cats.implicits._
import cats.mtl.Raise
import cats.{Applicative, Monad}
import com.uptech.windalerts.core.AlertNotFoundError
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.alerts.{AlertRequest, AlertsRepository, TimeRange}
import com.uptech.windalerts.infrastructure.Environment.EnvironmentAsk
import io.scalaland.chimney.dsl._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}

import scala.concurrent.ExecutionContext.Implicits.global

class MongoAlertsRepository[F[_] : EnvironmentAsk : Monad : Async : ContextShift](implicit M: Monad[F]) extends AlertsRepository[F] {
  private val env = implicitly[EnvironmentAsk[F]]

  def getCollection(): F[MongoCollection[DBAlert]] = {
    MongoRepository.getCollection("alerts")
  }

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
    for {
      collection <- getCollection()
      alert <- Async.fromFuture(M.pure(collection.replaceOne(equal("_id", new ObjectId(alertId)), DBAlert(alert)).toFuture().map(_ => alert)))
    } yield alert
  }

  def getById(id: String): OptionT[F, Alert] = {
    OptionT(for {
      collection <- getCollection()
      alert <- Async.fromFuture(M.pure(collection.find(equal("_id", new ObjectId(id))).toFuture().map(_.headOption.map(_.toAlert()))))
    } yield alert)
  }

  override def getAllEnabled(): F[Seq[Alert]] = {
    for {
      collection <- getCollection()
      all <- Async.fromFuture(M.pure(collection.find(equal("enabled", true)).toFuture())).map(_.map(_.toAlert()))
    } yield all
  }

  override def create(alertRequest: AlertRequest, user: String): F[Alert] = {
    val dBAlert = DBAlert(alertRequest, user)
    for {
      collection <- getCollection()
      alert <- Async.fromFuture(M.pure(collection.insertOne(dBAlert).toFuture().map(_ => dBAlert.toAlert())))
    } yield alert
  }

  override def delete(requester: String, alertId: String)(implicit FR: Raise[F, AlertNotFoundError]): F[Unit] = {
    delete_(requester, alertId)
  }

  def delete_(requester: String, alertId: String)(implicit A: Applicative[F], FR: Raise[F, AlertNotFoundError]): F[Unit] = {
    for {
      collection <- getCollection()
      dbAlert <- getById(alertId).getOrElseF(FR.raise(AlertNotFoundError()))
      _ <- if (dbAlert.owner != requester) FR.raise(AlertNotFoundError()) else A.pure(dbAlert)
      deleted <- Async.fromFuture(M.pure(collection.deleteOne(equal("_id", new ObjectId(alertId))).toFuture().map(_ => ())))
    } yield deleted
  }

  override def update(requester: String, alertId: String, updateAlertRequest: AlertRequest)(implicit FR: Raise[F, AlertNotFoundError]): F[Alert] = {
    for {
      collection <- getCollection()
      oldAlert <- getById(alertId).getOrElseF(FR.raise(AlertNotFoundError()))
      alertUpdated = DBAlert(updateAlertRequest, requester, alertId, oldAlert.createdAt)
      _ <- M.pure(collection.replaceOne(equal("_id", new ObjectId(alertId)), alertUpdated).toFuture())
      alert <- getById(alertId).getOrElseF(FR.raise(AlertNotFoundError()))
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

  private def findByCriteria(criteria: Bson) = {
    for {
      collection <- getCollection()
      all <- Async.fromFuture(M.pure(collection.find(criteria).toFuture())).map(_.map(_.toAlert()))
    } yield all
  }

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

  def apply(alertRequest: AlertRequest, user: String, alertId: String, createdAt: Long): DBAlert = {
    alertRequest.into[DBAlert]
      .withFieldComputed(_.owner, _ => user)
      .withFieldComputed(_._id, _ => new ObjectId(alertId))
      .withFieldComputed(_.createdAt, _ => createdAt)
      .transform
  }
}