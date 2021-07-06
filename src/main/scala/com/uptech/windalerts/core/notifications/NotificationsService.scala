package com.uptech.windalerts.core.notifications

import cats.data.EitherT
import cats.effect.{Async, Sync}
import cats.implicits._
import com.uptech.windalerts.core.alerts.AlertsService
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.core.beaches.domain._
import com.uptech.windalerts.core.notifications.NotificationsSender.NotificationDetails
import com.uptech.windalerts.core.user.{UserId, UserRepository, UserT}
import com.uptech.windalerts.infrastructure.repositories.mongo.Repos
import org.log4s.getLogger

import scala.util.Try

class NotificationsService[F[_] : Sync](U:UserRepository[F],  A: AlertsService[F], B: BeachService[F], repos: Repos[F], notificationSender: NotificationsSender[F])
                                       (implicit F: Async[F]){
  private val logger = getLogger

  final case class AlertWithBeach(alert: Alert, beach: Beach)

  final case class AlertWithUserWithBeach(alert: Alert, user: UserT, beach: Beach)

  def sendNotification() = {

    val usersToBeNotifiedEitherT: EitherT[F, Exception, List[Try[String]]] = for {
      alerts <- A.getAllForDayAndTimeRange

      alertsByBeaches = alerts.groupBy(_.beachId).map(kv => (BeachId(kv._1), kv._2))
      _ <- EitherT.liftF(F.delay(logger.error(s"alertsByBeaches ${alertsByBeaches.mapValues(v => v.map(_.beachId)).mkString}")))

      beaches <- B.getAll(alertsByBeaches.keys.toSeq)
      alertsToBeNotified = alertsByBeaches.map(kv => (beaches(kv._1), kv._2)).map(kv => (kv._1, kv._2.filter(_.isToBeNotified(kv._1)).map(a => AlertWithBeach(a, kv._1))))
      _ <- EitherT.liftF(F.delay(logger.error(s"alertsToBeNotified ${alertsToBeNotified.map(_._2.map(_.alert._id)).mkString}")))
      usersToBeNotified <- alertsToBeNotified.values.flatten.map(v => U.getByUserIdEitherT(v.alert.owner)).toList.sequence
      userIdToUser = usersToBeNotified.map(u => (u._id.toHexString, u)).toMap
      alertWithUserWithBeach = alertsToBeNotified.values.flatten.map(v => AlertWithUserWithBeach(v.alert, userIdToUser(v.alert.owner), v.beach))
      _ <- EitherT.liftF(F.delay(logger.error(s"alertWithUserWithBeach ${alertWithUserWithBeach.map(_.alert._id).mkString}")))

      usersToBeDisabledAlertsFiltered = alertWithUserWithBeach.filterNot(f => f.user.disableAllAlerts)
      _ <- EitherT.liftF(F.delay(logger.error(s"usersToBeDisabledAlertsFiltered ${usersToBeDisabledAlertsFiltered.map(_.alert._id).mkString}")))

      usersToBeNotifiedSnoozeFiltered = usersToBeDisabledAlertsFiltered.filterNot(f => f.user.snoozeTill > System.currentTimeMillis())
      _ <- EitherT.liftF(F.delay(logger.error(s"usersToBeNotifiedSnoozeFiltered ${usersToBeNotifiedSnoozeFiltered.map(_.alert._id).mkString}")))

      loggedOutUserFiltered = usersToBeNotifiedSnoozeFiltered.filterNot(f => f.user.deviceToken == null || f.user.deviceToken.isEmpty())
      _ <- EitherT.liftF(F.delay(logger.error(s"loggedOutUserFiltered ${loggedOutUserFiltered.map(_.user.email).mkString}")))

      usersWithCounts <- loggedOutUserFiltered.map(u => repos.notificationsRepo().countNotificationInLastHour(u.user._id.toHexString)).toList.sequence
      usersWithCountsMap = usersWithCounts.map(u => (u.userId, u.count)).toMap
      usersToBeFilteredWithCount = loggedOutUserFiltered.filter(u => usersWithCountsMap(u.user._id.toHexString) < u.user.notificationsPerHour)
      _ = EitherT.liftF(F.delay(logger.error(s"usersToBeFilteredWithCount ${usersToBeFilteredWithCount.map(_.alert._id).mkString}")))

      submitted <- EitherT.liftF(usersToBeFilteredWithCount.map(u => submit(u)).toList.sequence)
    } yield submitted
    usersToBeNotifiedEitherT

  }

  private def submit(u: AlertWithUserWithBeach) = {
    notificationSender.send(NotificationDetails(BeachId(u.alert.beachId), u.user.deviceToken, UserId(u.user._id.toHexString)))
  }


}
