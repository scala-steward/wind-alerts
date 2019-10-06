package com.uptech.windalerts.alerts

import cats.effect.IO
import cats.implicits._
import com.google.firebase.messaging.{FirebaseMessaging, Message, Notification}
import com.uptech.windalerts.domain.domain.BeachId
import com.uptech.windalerts.domain.{domain, HttpErrorHandler}
import com.uptech.windalerts.status.Beaches
import com.uptech.windalerts.users.{Devices, Users, UsersRepository}
import org.log4s.getLogger

class Notifications(A: AlertsService.Service, B: Beaches.Service, U : Users.Service, D:Devices.Service, UR:UsersRepository.Repository, firebaseMessaging: FirebaseMessaging, H:HttpErrorHandler[IO]) {
  private val logger = getLogger

  def sendNotification = {
    val usersToBeNotified = for {
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
        val maybeUser = UR.getById(alert.owner)
        logger.info(s"Maybeuser $maybeUser")
        maybeUser.map(userIO => userIO.map(user => domain.AlertWithUser(alert, user)))
      }).toList)

      usersToBeNotifiedSeq <- usersToBeNotified.sequence
      log <- IO(logger.info(s"usersToBeNotifiedSeq $usersToBeNotifiedSeq"))
      usersToBeNotifiedFlattend <- IO(usersToBeNotifiedSeq.flatten)

      sendNotifications <- IO(usersToBeNotifiedFlattend.foreach(u => {
        val msg = Message.builder()
          .setNotification(new Notification(
            s"Wind Alert on ${u.alert.beachId}",
            "Surf Time."))
          .setToken(u.user.deviceToken)
          .build()
        logger.info(s"notifying ${firebaseMessaging.send(msg)}")
      }))
    } yield sendNotifications
    usersToBeNotified
  }

  private def toIOMap(m: Map[IO[domain.Beach], Seq[domain.Alert]]) = {
    m.toList.traverse {
      case (io, s) => io.map(s2 => (s2, s))
    }.map {
      _.toMap
    }
  }

}
