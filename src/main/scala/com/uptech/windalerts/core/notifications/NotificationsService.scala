package com.uptech.windalerts.core.notifications

import cats.data.EitherT
import cats.effect.{Async, Sync}
import cats.implicits._
import com.uptech.windalerts.core.alerts.AlertsRepository
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.core.beaches.domain._
import com.uptech.windalerts.core.notifications.NotificationsSender.NotificationDetails
import com.uptech.windalerts.core.user.{UserId, UserRepository, UserT}
import com.uptech.windalerts.logger

class NotificationsService[F[_] : Sync](N: NotificationRepository[F],
                                        U: UserRepository[F],
                                        B: BeachService[F],
                                        alertsRepository: AlertsRepository[F],
                                        notificationSender: NotificationsSender[F])(implicit F: Async[F]) {

  final case class AlertWithBeach(alert: Alert, beach: Beach)

  final case class AlertWithUserWithBeach(alert: Alert, user: UserT, beach: Beach)

  def sendNotification() = {
    EitherT.liftF(for {
      usersReadyToRecieveNotifications <- allLoggedInUsersReadyToRecieveNotifications()
      alertsByBeaches <- alertsForUsers(usersReadyToRecieveNotifications)
      beaches <- beachStatuses(alertsByBeaches.keys.toSeq)
      userIdToUser = usersReadyToRecieveNotifications.map(u => (u._id.toHexString, u)).toMap
      alertsToBeNotified = alertsByBeaches
        .map(kv => (beaches(kv._1), kv._2))
        .map(kv => (kv._1, kv._2.filter(_.isToBeNotified(kv._1)).map(AlertWithBeach(_, kv._1))))
      _ <- F.delay(logger.info(s"alertsToBeNotified : ${alertsToBeNotified.values.map(_.flatMap(_.alert._id.toHexString)).mkString(", ")}"))
      alertWithUserWithBeach = alertsToBeNotified.values.flatten.map(v => AlertWithUserWithBeach(v.alert, userIdToUser(v.alert.owner), v.beach))
      submitted <- alertWithUserWithBeach.map(submit(_)).toList.sequence.getOrElse(())
    } yield submitted)
  }


  private def allLoggedInUsersReadyToRecieveNotifications() = {
    for {
      users <- U.loggedInUsersWithNotificationsNotDisabledAndNotSnoozed()
      _ <- F.delay(logger.info(s"loggedInUsersWithNotificationsNotDisabledAndNotSnoozed : ${users.map(_._id.toHexString).mkString(", ")}"))
      usersWithLastHourNotificationCounts <- users.map(u => N.countNotificationInLastHour(u._id.toHexString)).toList.sequence
      zipped = users.zip(usersWithLastHourNotificationCounts)
      usersReadyToReceiveNotifications = zipped.filter(u => u._2.count < u._1.notificationsPerHour).map(_._1)
      _ <- F.delay(logger.info(s"usersReadyToReceiveNotifications : ${usersReadyToReceiveNotifications.map(_._id.toHexString).mkString(", ")}"))
    } yield usersReadyToReceiveNotifications
  }

  private def alertsForUsers(users: Seq[UserT]) = {
    for {
      alertsForUsers <- users.map(u => alertsRepository.getAllEnabledForUser(u._id.toHexString)).sequence.map(_.flatten)
      alertsByBeaches = alertsForUsers.groupBy(_.beachId).map(kv => (BeachId(kv._1), kv._2))
      _ <- F.delay(logger.info(s"alertsByBeaches : ${alertsForUsers.map(_._id.toHexString).mkString(", ")}"))
    } yield alertsByBeaches
  }

  private def beachStatuses(beachIds: Seq[BeachId]) = {
    B.getAll(beachIds).value.map(_.leftMap(e => {
      logger.warn(s"Error while fetching beach status $e")
    }).getOrElse(Map()))
  }

  private def submit(u: AlertWithUserWithBeach):EitherT[F, Throwable, Unit] = {
    for {
      _ <- notificationSender.send(NotificationDetails(BeachId(u.alert.beachId), u.user.deviceToken, UserId(u.user._id.toHexString)))
      _ <- EitherT.liftF(N.create(Notification(u.alert._id.toHexString, u.user._id.toHexString, u.user.deviceToken,  System.currentTimeMillis())))
    } yield ()
  }

}
