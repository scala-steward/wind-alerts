package com.uptech.windalerts.notifications

import java.util.regex.Pattern

import cats.effect.IO
import cats.implicits._
import com.google.firebase.messaging.{FirebaseMessaging, Message}
import com.uptech.windalerts.alerts.AlertsService
import com.uptech.windalerts.domain.beaches.Beach
import com.uptech.windalerts.domain.config.AppConfig
import com.uptech.windalerts.domain.conversions.toIO
import com.uptech.windalerts.domain.domain.{Alert, BeachId, User}
import com.uptech.windalerts.domain.{HttpErrorHandler, beaches, config, domain}
import com.uptech.windalerts.status.Beaches
import com.uptech.windalerts.users.UserRepositoryAlgebra
import org.log4s.getLogger

class Notifications(A: AlertsService.Service, B: Beaches.Service, beaches: Map[Long, Beach], UR: UserRepositoryAlgebra, firebaseMessaging: FirebaseMessaging, H: HttpErrorHandler[IO], notificationsRepository: NotificationRepository,
                    config: AppConfig) {
  private val logger = getLogger

  def sendNotification = {
    for {
      alerts <- A.getAllForDayAndTimeRange
      alertsByBeaches <- IO(alerts.groupBy(_.beachId).map(
        kv => {
          (B.get(BeachId(kv._1.toInt)), kv._2)
        }))
      asIOMap <- toIOMap(alertsByBeaches)
      _ <- IO(logger.info(s"alertsByBeaches $alertsByBeaches"))
      alertsToBeNotified <- IO(asIOMap.map(kv => (kv._1, kv._2.filter(_.isToBeNotified(kv._1)))))
      _ <- IO(logger.info(s"alertsToBeNotified ${alertsToBeNotified}"))

      usersToBeNotified <- IO(alertsToBeNotified.values.flatMap(elem => elem).map(alert => {
        val maybeUser = UR.getByUserId(alert.owner)
        logger.info(s"Maybe user $maybeUser")
        maybeUser.map(userIO => userIO.map(user => domain.AlertWithUser(alert, user)))
      }).toList)

      usersToBeNotifiedSeq <- usersToBeNotified.sequence
      _ <- IO(logger.info(s"usersToBeNotifiedSeq $usersToBeNotifiedSeq"))

      usersToBeNotifiedFlattend <- IO(usersToBeNotifiedSeq.flatten)
      _ <- IO(logger.info(s"usersToBeNotifiedFlattend $usersToBeNotifiedFlattend"))

      usersToBeNotifiedSnoozeFiltered <- IO(usersToBeNotifiedFlattend.filterNot(f => f.user.snoozeTill > System.currentTimeMillis()))
      usersToBeNotifiedSnoozeFilteredWithCount <- IO(usersToBeNotifiedSnoozeFiltered.map(f => {
        val io = notificationsRepository.countNotificationInLastHour(f.alert.id)
        io.map(x => (f, x))
      }))
      usersToBeNotifiedSnoozeFilteredWithCountIO <- toIO(usersToBeNotifiedSnoozeFilteredWithCount)
      log <- IO(logger.info(s"usersToBeNotifiedSnoozeFilteredWithCountIO $usersToBeNotifiedSnoozeFilteredWithCountIO"))

      alertsByUserNotificationSettings <- IO(usersToBeNotifiedSnoozeFilteredWithCountIO.filter(x => x._2 < x._1.user.notificationsPerHour).map(x => x._1))

      log <- IO(logger.info(s"alertsByUserNotificationSettings $alertsByUserNotificationSettings"))
      save <- IO(alertsByUserNotificationSettings.map(u => {
        val beachName = beaches(u.alert.beachId).location
        tryS(u.alert.beachId, config.surfsUp.notifications.title.replaceAll("BEACH_NAME", beachName),
          config.surfsUp.notifications.body, u.user, u.alert)
      }
      )
      )

    } yield ()
  }


  private def tryS(beachId:Long, title: String, body: String, u: User, a: Alert): Either[Exception, String] = {
    try Right {
      val sent = firebaseMessaging.send(Message.builder()
        .putData("beachId", s"$beachId")
        .setNotification(new com.google.firebase.messaging.Notification(title, body))
        .setToken(u.deviceToken)
        .build())
      val s = notificationsRepository.create(com.uptech.windalerts.domain.domain.Notification(None, a.id, a.owner, u.deviceToken, title, body, System.currentTimeMillis()))
      logger.warn(s"${s.unsafeRunSync()}")

      logger.warn(s" sent ${sent}")

      sent
    }

    catch {
      case e: Exception => {
        logger.error(s"Error $e")
        Left(e)
      }
    }
  }

  private def toIOMap(m: Map[IO[domain.Beach], Seq[domain.Alert]]) = {
    m.toList.traverse {
      case (io, s) => io.map(s2 => (s2, s))
    }.map {
      _.toMap
    }
  }

}
