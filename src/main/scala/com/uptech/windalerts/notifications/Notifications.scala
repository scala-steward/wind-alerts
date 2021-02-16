package com.uptech.windalerts.notifications

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import com.google.firebase.messaging._
import com.uptech.windalerts.Repos
import com.uptech.windalerts.core.AlertsService
import com.uptech.windalerts.core.beaches.BeachService
import com.uptech.windalerts.core.domain.AlertT
import com.uptech.windalerts.domain.beaches.Beach
import com.uptech.windalerts.domain.config.AppConfig
import com.uptech.windalerts.domain.domain.{AlertWithUserWithBeach, BeachId, UserT}
import com.uptech.windalerts.domain.{HttpErrorHandler, domain}
import org.log4s.getLogger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Notifications(A: AlertsService[IO], B: BeachService[IO], beaches: Map[Long, Beach], repos:Repos[IO], firebaseMessaging: FirebaseMessaging, H: HttpErrorHandler[IO],
                    config: AppConfig) {
  private val logger = getLogger


  def sendNotification = {
    val usersToBeNotifiedEitherT = for {
      alerts                          <- A.getAllForDayAndTimeRange
      alertsByBeaches                 =  alerts.groupBy(_.beachId).map(kv => (BeachId(kv._1), kv._2))
      _                               <- EitherT.liftF(IO(logger.error(s"alertsByBeaches ${alertsByBeaches.mapValues(v=>v.map(_.beachId)).mkString}")))
      beaches                         <- B.getAll(alertsByBeaches.keys.toSeq)
      x                               =  alertsByBeaches.map(kv => (beaches(kv._1), kv._2))
      alertsToBeNotified              =  x.map(kv => (kv._1, kv._2.filter(_.isToBeNotified(kv._1)).map(a => domain.AlertWithBeach(a, kv._1))))
      _                               <- EitherT.liftF(IO(logger.error(s"alertsToBeNotified ${alertsToBeNotified.map(_._2.map(_.alert._id)).mkString}")))
      usersToBeNotified               <- alertsToBeNotified.values.flatten.map(v => repos.usersRepo().getByUserIdEitherT(v.alert.owner)).toList.sequence
      userIdToUser                    =  usersToBeNotified.map(u => (u._id.toHexString, u)).toMap
      alertWithUserWithBeach          =  alertsToBeNotified.values.flatten.map(v => AlertWithUserWithBeach(v.alert, userIdToUser(v.alert.owner), v.beach))
      _                               <- EitherT.liftF(IO(logger.error(s"alertWithUserWithBeach ${alertWithUserWithBeach.map(_.alert._id).mkString}")))
      usersToBeDisabledAlertsFiltered =  alertWithUserWithBeach.filterNot(f => f.user.disableAllAlerts)
      _                               <- EitherT.liftF(IO(logger.error(s"usersToBeDisabledAlertsFiltered ${usersToBeDisabledAlertsFiltered.map(_.alert._id).mkString}")))
      usersToBeNotifiedSnoozeFiltered =  usersToBeDisabledAlertsFiltered.filterNot(f => f.user.snoozeTill > System.currentTimeMillis())
      _                               <- EitherT.liftF(IO(logger.error(s"usersToBeNotifiedSnoozeFiltered ${usersToBeNotifiedSnoozeFiltered.map(_.alert._id).mkString}")))
      loggedOutUserFiltered           =  usersToBeNotifiedSnoozeFiltered.filterNot(f => f.user.deviceToken == null || f.user.deviceToken.isEmpty())
      _                               <- EitherT.liftF(IO(logger.error(s"loggedOutUserFiltered ${loggedOutUserFiltered.map(_.alert._id).mkString}")))
      usersWithCounts                 <- loggedOutUserFiltered.map(u => repos.notificationsRepo().countNotificationInLastHour(u.user._id.toHexString)).toList.sequence
      usersWithCountsMap              =  usersWithCounts.map(u => (u.userId, u.count)).toMap
      usersToBeFilteredWithCount      =  loggedOutUserFiltered.filter(u => usersWithCountsMap(u.user._id.toHexString) < u.user.notificationsPerHour)
      _                               = EitherT.liftF(IO(logger.error(s"usersToBeFilteredWithCount ${usersToBeFilteredWithCount.map(_.alert._id).mkString}")))

    } yield usersToBeFilteredWithCount

    for {
      submittedTasks <- usersToBeNotifiedEitherT.map(l => Future (l.map(submit(_))))
      result          = submittedTasks.onComplete(s => logger.error(s"Result : ${s.toEither.toOption}"))
    } yield result
  }


  private def submit(u: AlertWithUserWithBeach) = {
    logger.error("Submitting " + u)

    Thread.sleep(2000)
    logger.error("Submitting " + u)
    val beachName = beaches(u.alert.beachId).location
    val title = config.surfsUp.notifications.title.replaceAll("BEACH_NAME", beachName)
    val body = config.surfsUp.notifications.body

    tryS(u.alert.beachId, title, body, u.user, u.alert)
  }

  private def tryS(beachId: Long, title: String, body: String, u: UserT, a: AlertT) = {
    try {
      logger.error(s" sending to ${u.email} for ${a._id.toHexString}")

      val sent = firebaseMessaging.send(Message.builder().setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
          .setWebpushConfig(WebpushConfig.builder().putHeader("Urgency", "high").build())
        .putData("beachId", s"$beachId")
        .setNotification(new com.google.firebase.messaging.Notification(title, body))
        .setToken(u.deviceToken)
          .setApnsConfig(ApnsConfig.builder()
            .setAps(Aps.builder().build())
            .putHeader("apns-priority", "10").build())
          .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
        .build())
      val s = repos.notificationsRepo().create(com.uptech.windalerts.domain.domain.Notification(a._id.toHexString, a.owner, u.deviceToken, title, body, System.currentTimeMillis()))
      logger.warn(s"unsafeRunSync ${s.unsafeRunSync()}")

      logger.warn(s" sending to ${u.email} for ${a._id.toHexString} status : ${sent}")
    }
    catch {
      case e: Exception => {
        logger.error(e)(s"error while sending to ${u.email} for ${a._id.toHexString}")
      }
    }
    finally {
      logger.error(s" Sent to ${u.email} for ${a._id.toHexString}")
    }
  }

}
