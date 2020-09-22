package com.uptech.windalerts.alerts

import cats.data.{EitherT, OptionT}
import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.alerts.domain.AlertT
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.{AlertNotFoundError, SurfsUpError, conversions, domain}
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

  def delete(requester: String, id: String): EitherT[F, SurfsUpError, Unit]

  def updateT(requester: String, alertId: String, updateAlertRequest: AlertRequest): EitherT[F, SurfsUpError, AlertT]
}