package com.uptech.windalerts.notifications

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import com.google.firebase.messaging.{FirebaseMessaging, Message}
import com.uptech.windalerts.alerts.AlertsService
import com.uptech.windalerts.domain.beaches.Beach
import com.uptech.windalerts.domain.config.AppConfig
import com.uptech.windalerts.domain.domain.{Alert, AlertWithUserWithBeach, BeachId, UserT}
import com.uptech.windalerts.domain.{HttpErrorHandler, domain}
import com.uptech.windalerts.status.BeachService
import com.uptech.windalerts.users.UserRepositoryAlgebra
import org.log4s.getLogger
import org.mongodb.scala.MongoClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Notifications(A: AlertsService.Service, B: BeachService[IO], beaches: Map[Long, Beach], UR: UserRepositoryAlgebra, firebaseMessaging: FirebaseMessaging, H: HttpErrorHandler[IO], notificationsRepository: MongoNotificationsRepository,
                    config: AppConfig) {
  private val logger = getLogger


  def sendNotification = {
    val usersToBeNotifiedEitherT = for {
      alerts                          <- EitherT.liftF(A.getAllForDayAndTimeRange)
      alertsByBeaches                 =  alerts.groupBy(_.beachId).map(kv => (BeachId(kv._1), kv._2))
      _                               <- EitherT.liftF(IO(logger.info(s"alertsByBeaches $alertsByBeaches")))
      beaches                         <- B.getAll(alertsByBeaches.keys.toSeq)
      x                               =  alertsByBeaches.map(kv => (beaches(kv._1), kv._2))
      alertsToBeNotified              =  x.map(kv => (kv._1, kv._2.filter(_.isToBeNotified(kv._1)).map(a => domain.AlertWithBeach(a, kv._1))))
      _                               <- EitherT.liftF(IO(logger.info(s"alertsToBeNotified $alertsToBeNotified")))
      usersToBeNotified               <- alertsToBeNotified.values.flatten.map(v => UR.getByUserIdEitherT(v.alert.owner)).toList.sequence
      userIdToUser                    =  usersToBeNotified.map(u => (u._id.toHexString, u)).toMap
      alertWithUserWithBeach          =  alertsToBeNotified.values.flatten.map(v => AlertWithUserWithBeach(v.alert, userIdToUser(v.alert.owner), v.beach))
      _                               <- EitherT.liftF(IO(logger.info(s"alertWithUserWithBeach $alertWithUserWithBeach")))
      usersToBeDisabledAlertsFiltered =  alertWithUserWithBeach.filterNot(f => f.user.disableAllAlerts)
      usersToBeNotifiedSnoozeFiltered =  alertWithUserWithBeach.filterNot(f => f.user.snoozeTill > System.currentTimeMillis())
      usersWithCounts                 <- usersToBeNotifiedSnoozeFiltered.map(u => notificationsRepository.countNotificationInLastHour(u.user._id.toHexString)).toList.sequence
      usersWithCountsMap              =  usersWithCounts.map(u => (u.userId, u.count)).toMap
      usersToBeFilteredWithCount      =  usersToBeNotifiedSnoozeFiltered.filter(u => usersWithCountsMap(u.user._id.toHexString) < u.user.notificationsPerHour)
    } yield usersToBeFilteredWithCount

    for {
      submittedTasks <- usersToBeNotifiedEitherT.map(l => Future (l.map(submit(_))))
      result          = submittedTasks.onComplete(s => logger.info(s"Result : ${s.toEither.toOption}"))
    } yield result
  }


  private def submit(u: AlertWithUserWithBeach) = {
    logger.info("Submitting " + u)

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

  private def tryS(beachId: Long, title: String, body: String, u: UserT, a: Alert) = {
    try {
      logger.warn(s" sending to ${u.email} for ${a.id}")

      val sent = firebaseMessaging.send(Message.builder()
        .putData("beachId", s"$beachId")
        .setNotification(new com.google.firebase.messaging.Notification(title, body))
        .setToken(u.deviceToken)
        .build())
      val s = notificationsRepository.create(com.uptech.windalerts.domain.domain.Notification(a.id, a.owner, u.deviceToken, title, body, System.currentTimeMillis()))
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
