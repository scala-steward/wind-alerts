package com.uptech.windalerts.core.notifications

import cats.effect.{Async, Sync}
import cats.implicits._
import cats.mtl.Raise
import cats.{Monad, Parallel}
import com.uptech.windalerts.core.BeachNotFoundError
import com.uptech.windalerts.core.alerts.AlertsRepository
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.core.beaches.domain._
import com.uptech.windalerts.core.notifications.NotificationsSender.NotificationDetails
import com.uptech.windalerts.core.refresh.tokens.UserSessionRepository
import com.uptech.windalerts.core.user.{UserId, UserRepository, UserT}
import com.uptech.windalerts.logger

class NotificationsService[F[_] : Sync : Parallel](N: NotificationRepository[F],
                                                   U: UserRepository[F],
                                                   B: BeachService[F],
                                                   alertsRepository: AlertsRepository[F],
                                                   notificationSender: NotificationsSender[F],
                                                   userSessionsRepository: UserSessionRepository[F])(implicit F: Async[F],  FR: Raise[F, BeachNotFoundError], M:Monad[F]) {
  final case class UserDetails(userId: String, email: String)

  final case class AlertWithBeach(alert: Alert, beach: Beach)

  final case class UserDetailsWithDeviceToken(userId: String, email: String, deviceToken: String, notificationsPerHour: Long)

  final case class AlertWithUserWithBeach(alert: Alert, user: UserDetailsWithDeviceToken, beach: Beach)


  def sendNotification() = {
    for {
      alertWithUserWithBeach <- findAllAlertsToNotify()
      submitted <- alertWithUserWithBeach.map(submit(_)).toList.sequence
    } yield submitted
  }

  def findAllAlertsToNotify() = {
    for {
      usersReadyToReceiveNotifications <- allLoggedInUsersReadyToReceiveNotifications()
      alertsByBeaches <- alertsForUsers(usersReadyToReceiveNotifications)
      beaches <- beachStatuses(alertsByBeaches.keys.toSeq)
      alertsToBeNotified = alertsByBeaches
        .map(kv => (beaches(kv._1), kv._2))
        .map(kv => (kv._1, kv._2.filter(_.isToBeNotified(kv._1)).map(AlertWithBeach(_, kv._1))))
      _ <- F.delay(logger.info(s"alertsToBeNotified : ${alertsToBeNotified.values.map(_.flatMap(_.alert.id)).mkString(", ")}"))
      userIdToUser = usersReadyToReceiveNotifications.map(u => (u.userId, u)).toMap
      alertWithUserWithBeach = alertsToBeNotified.values.flatten.map(v => AlertWithUserWithBeach(v.alert, userIdToUser(v.alert.owner), v.beach))
    } yield alertWithUserWithBeach
  }


  private def allLoggedInUsersReadyToReceiveNotifications() = {
    for {
      usersWithNotificationsEnabledAndNotSnoozed <- U.findUsersWithNotificationsEnabledAndNotSnoozed()
      _ <- F.delay(logger.info(s"usersWithNotificationsEnabledAndNotSnoozed : ${usersWithNotificationsEnabledAndNotSnoozed.map(_.id).mkString(", ")}"))

      loggedInUsers <- filterLoggedOutUsers(usersWithNotificationsEnabledAndNotSnoozed)

      usersWithLastHourNotificationCounts <- loggedInUsers.map(u => N.countNotificationsInLastHour(u.userId)).toList.sequence
      zipped = loggedInUsers.zip(usersWithLastHourNotificationCounts)

      usersReadyToReceiveNotifications = zipped.filter(u => u._2.count < u._1.notificationsPerHour).map(_._1)
      _ <- F.delay(logger.info(s"usersReadyToReceiveNotifications : ${usersReadyToReceiveNotifications.map(_.userId).mkString(", ")}"))
    } yield usersReadyToReceiveNotifications
  }

  private def filterLoggedOutUsers(usersWithNotificationsEnabledAndNotSnoozed: Seq[UserT]) = {
    for {
      userSessions <- usersWithNotificationsEnabledAndNotSnoozed.map(u => userSessionsRepository.getByUserId(u.id).value).toList.sequence
      usersWithSession = usersWithNotificationsEnabledAndNotSnoozed.zip(userSessions)
      loggedInUsers = usersWithSession.filter(_._2.isDefined).map(u => UserDetailsWithDeviceToken(u._1.id, u._1.email, u._2.get.deviceToken, u._1.notificationsPerHour))
    } yield loggedInUsers
  }

  private def alertsForUsers(users: Seq[UserDetailsWithDeviceToken]) = {
    for {
      alertsForUsers <- users.map(u => alertsRepository.getAllEnabledForUser(u.userId)).sequence.map(_.flatten)
      alertsForUsersWithMatchingTime = alertsForUsers.toList.filter(_.isTimeMatch())
      alertsByBeaches = alertsForUsersWithMatchingTime.groupBy(_.beachId).map(kv => (BeachId(kv._1), kv._2))
      _ <- F.delay(logger.info(s"alertsForUsersWithMathcingTime : ${alertsForUsersWithMatchingTime.map(_.id).mkString(", ")}"))
    } yield alertsByBeaches
  }

  private def beachStatuses(beachIds: Seq[BeachId]) = {
    B.getAll(beachIds)
  }

  private def submit(u: AlertWithUserWithBeach): F[Unit] = {
    for {
      status <- notificationSender.send(NotificationDetails(BeachId(u.alert.beachId), u.user.deviceToken, UserId(u.user.userId)))
      _ <- F.delay(logger.info(s"Notification response to ${u.user.email} is ${status}"))
      _ <- N.create(u.alert.id, u.user.userId, u.user.deviceToken, System.currentTimeMillis())
    } yield ()
  }


}
