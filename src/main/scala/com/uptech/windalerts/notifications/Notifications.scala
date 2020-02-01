package com.uptech.windalerts.notifications

import java.util.regex.Pattern

import scala.concurrent.ExecutionContext.Implicits.global
import cats.effect.IO
import cats.implicits._
import com.google.firebase.messaging.{FirebaseMessaging, Message}
import com.uptech.windalerts.alerts.AlertsService
import com.uptech.windalerts.domain.beaches.Beach
import com.uptech.windalerts.domain.config.AppConfig
import com.uptech.windalerts.domain.conversions.toIO
import com.uptech.windalerts.domain.domain.{Alert, AlertWithBeach, BeachId, User}
import com.uptech.windalerts.domain.{HttpErrorHandler, beaches, config, domain}
import com.uptech.windalerts.status.Beaches
import com.uptech.windalerts.users.UserRepositoryAlgebra
import org.log4s.getLogger

import scala.concurrent.Future

class Notifications(A: AlertsService.Service, B: Beaches.Service, beaches: Map[Long, Beach], UR: UserRepositoryAlgebra, firebaseMessaging: FirebaseMessaging, H: HttpErrorHandler[IO], notificationsRepository: NotificationRepository,
                    config: AppConfig) {
  private val logger = getLogger

  def sendNotification = {
    val x = for {
      alerts <- A.getAllForDayAndTimeRange
      alertsByBeaches <- IO(alerts.groupBy(_.beachId).map(
        kv => {
          (B.get(BeachId(kv._1.toInt)), kv._2)
        }))
      asIOMap <- toIOMap(alertsByBeaches)
      _ <- IO(logger.info(s"alertsByBeaches $alertsByBeaches"))
      alertsToBeNotified <- IO(asIOMap.map(kv => (kv._1, kv._2.filter(_.isToBeNotified(kv._1)).map(a => domain.AlertWithBeach(a, kv._1)))))
      _ <- IO(logger.info(s"alertsToBeNotified ${alertsToBeNotified}"))

      //      usersToBeNotified <- IO(alertsToBeNotified.map(k=>(k._1,k._2)).values.flatMap(elem => elem).map(alert => {
      //        val maybeUser = UR.getByUserId(alert._2.owner)
      //        logger.info(s"Maybe user $maybeUser")
      //        maybeUser.map(userIO => userIO.map(user => domain.AlertWithUser(alert, user)))
      //      }).toList)

      usersToBeNotified <- IO(alertsToBeNotified.values.flatMap(elem => elem).map(alert => {
        val maybeUser = UR.getByUserId(alert.alert.owner)
        logger.info(s"Maybe user $maybeUser")
        maybeUser.map(userIO => userIO.map(user => domain.AlertWithUserWithBeach(alert.alert, user, alert.beach)))
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

    } yield alertsByUserNotificationSettings

    val x1 = for {
      a1 <- x.map(l =>
        Future {
          {
            l.map(u => {
              logger.info("Submitting " + u)

              //              swellDirections.contains(beach.tide.swell.directionText) &&
              //                waveHeightFrom <= beach.tide.swell.height && waveHeightTo >= beach.tide.swell.height &&
              //                windDirections.contains(beach.wind.directionText) &&
              //                tideHeightStatuses.contains(beach.tide.height.status)

              Thread.sleep(2000)
              logger.info("Submitting " + u)
              val beachName = beaches(u.alert.beachId).location
              val body = config.surfsUp.notifications.title.replaceAll("BEACH_NAME", beachName)
              val fullBody =
                s"""windDirections : ${u.alert.windDirections.mkString(", ")} - ${u.beach.wind.directionText}
                tideHeightStatuses : ${u.alert.tideHeightStatuses.mkString(", ")} - ${u.beach.tide.height}
                days : ${u.alert.days.mkString(", ")}
                swellDirections : ${u.alert.swellDirections.mkString(", ")}  - ${u.beach.tide.swell.directionText}
                waveHeightFrom : ${u.alert.waveHeightFrom} - ${u.beach.tide.swell.height}
                waveHeightTo : ${u.alert.waveHeightTo} - ${u.beach.tide.swell.height}
                timeRanges : ${u.alert.timeRanges.mkString(", ")}
                """

              tryS(u.alert.beachId, body, fullBody, u.user, u.alert)
            }

            )
          }
        })
      a2: Unit = a1.onComplete(s => {
        logger.info("Result : " + s.toEither.toOption)
      })

    } yield a2
    x1
  }


  private def tryS(beachId: Long, title: String, body: String, u: User, a: Alert) = {
    try {
      logger.warn(s" sending to ${u.email} for ${a.id}")

      val sent = firebaseMessaging.send(Message.builder()
        .putData("beachId", s"$beachId")
        .setNotification(new com.google.firebase.messaging.Notification(title, body))
        .setToken(u.deviceToken)
        .build())
      val s = notificationsRepository.create(com.uptech.windalerts.domain.domain.Notification(None, a.id, a.owner, u.deviceToken, title, body, System.currentTimeMillis()))
      logger.warn(s"unsafeRunSync ${s.unsafeRunSync()}")

      logger.warn(s" sending to ${u.email} for ${a.id} status : ${sent}")
    }
    catch {
      case e: Exception => {
        logger.error(s"Error $e")

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
