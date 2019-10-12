package com.uptech.windalerts.notifications

import cats.effect.IO
import cats.implicits._
import com.google.firebase.messaging.{FirebaseMessaging, Message, Notification}
import com.uptech.windalerts.alerts.AlertsService
import com.uptech.windalerts.domain.domain.{BeachId, Notification, User}
import com.uptech.windalerts.domain.{HttpErrorHandler, domain}
import com.uptech.windalerts.status.Beaches
import com.uptech.windalerts.users.UserRepositoryAlgebra
import org.log4s.getLogger

class Notifications(A: AlertsService.Service, B: Beaches.Service, UR: UserRepositoryAlgebra, firebaseMessaging: FirebaseMessaging, H: HttpErrorHandler[IO], notificationsRepository: NotificationRepository) {
  private val logger = getLogger

  def sendNotification = {
    for {
      alerts <- A.getAllForDay
      alertsByBeaches <- IO(alerts.groupBy(_.beachId).map(
        kv => {
          (B.get(BeachId(kv._1.toInt)), kv._2)
        }))
      asIOMap <- toIOMap(alertsByBeaches)
      log <- IO(logger.info(s"alertsByBeaches $alertsByBeaches"))
      alertsToBeNotified <- IO(asIOMap.map(kv => (kv._1, kv._2.filter(_.isToBeNotified(kv._1)))))
      log <- IO(logger.info(s"alertsByBeaches ${alertsByBeaches.keys}"))

      usersToBeNotified <- IO(alertsToBeNotified.values.flatMap(elem => elem).map(alert => {
        val maybeUser = UR.getByUserId(alert.owner)
        logger.info(s"Maybeuser $maybeUser")
        maybeUser.map(userIO => userIO.map(user => domain.AlertWithUser(alert, user)))
      }).toList)

      usersToBeNotifiedSeq <- usersToBeNotified.sequence
      log <- IO(logger.info(s"usersToBeNotifiedSeq $usersToBeNotifiedSeq"))

      usersToBeNotifiedFlattend <- IO(usersToBeNotifiedSeq.flatten)
      log <- IO(logger.info(s"usersToBeNotifiedFlattend $usersToBeNotifiedFlattend"))

      usersToBeNotifiedSnoozeFiltered <- IO(usersToBeNotifiedFlattend.filterNot(f => f.user.snoozeTill > System.currentTimeMillis()))
      usersToBeNotifiedSnoozeFilteredWithCount <- IO(usersToBeNotifiedFlattend.map(f => {
        val io = notificationsRepository.countNotificationInLastHour(f.user.id)
        io.map(x=>(f, x))
      }))
      usersToBeNotifiedSnoozeFilteredWithCountIO <- com.uptech.windalerts.domain.conversions.toIO(usersToBeNotifiedSnoozeFilteredWithCount)
      log <- IO(logger.info(s"usersToBeNotifiedSnoozeFilteredWithCountIO $usersToBeNotifiedSnoozeFilteredWithCountIO"))

      alertsByUserNotificationSettings <- IO(usersToBeNotifiedSnoozeFilteredWithCountIO.filter(x=>x._2 < x._1.alert.notificationsPerHour).map(x => x._1))


      log <- IO(logger.info(s"alertsByUserNotificationSettings $alertsByUserNotificationSettings"))
      save <- IO(usersToBeNotifiedSnoozeFiltered.map(u => tryS(s"Wind Alert on ${u.alert.beachId}", "Surf Time.", u.user)))

    } yield ()
  }


  private def tryS(title: String, body: String, u: User): Either[Exception, String] = {
    try Right{
      val s= notificationsRepository.create(com.uptech.windalerts.domain.domain.Notification(None, u.id, u.deviceToken, title, body, System.currentTimeMillis()))
      println(s.unsafeRunSync())
      firebaseMessaging.send(Message.builder()
      .setNotification(new com.google.firebase.messaging.Notification(title, body))
      .setToken(u.deviceToken)
      .build())}
     catch {
      case e: Exception => Left(e)
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
