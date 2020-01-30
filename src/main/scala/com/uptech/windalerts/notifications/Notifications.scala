package com.uptech.windalerts.notifications

import cats.data.EitherT
import cats.effect.{IO, LiftIO}
import cats.implicits._
import com.google.firebase.messaging.{FirebaseMessaging, Message}
import com.uptech.windalerts.alerts.AlertsService
import com.uptech.windalerts.domain.beaches.Beach
import com.uptech.windalerts.domain.config.AppConfig
import com.uptech.windalerts.domain.conversions.toIO
import com.uptech.windalerts.domain.domain.{Alert, BeachId, User}
import com.uptech.windalerts.domain.{HttpErrorHandler, domain}
import com.uptech.windalerts.status.BeachService
import com.uptech.windalerts.users.UserRepositoryAlgebra
import org.log4s.getLogger

class Notifications(A: AlertsService.Service, B: BeachService[IO], beaches: Map[Long, Beach], UR: UserRepositoryAlgebra, firebaseMessaging: FirebaseMessaging, H: HttpErrorHandler[IO], notificationsRepository: NotificationRepository,
                    config: AppConfig) {
  private val logger = getLogger

  def sendNotification = {
    for {
      alerts <- EitherT.liftF(A.getAllForDayAndTimeRange)
      alertsByBeaches <- EitherT.liftF(IO(alerts.groupBy(_.beachId).map(
        kv => {
          (B.get(BeachId(kv._1.toInt)), kv._2)
        })))
      asIOMap <- toIOMap(alertsByBeaches)
//      _ <- EitherT.liftF(IO(logger.info(s"alertsByBeaches $alertsByBeaches")))
//      alertsToBeNotified <- EitherT.liftF(IO(asIOMap.map(kv => (kv._1, kv._2.filter(_.isToBeNotified(kv._1))))))
//      _ <- EitherT.liftF(IO(logger.info(s"alertsToBeNotified ${alertsToBeNotified}")))
//
//      usersToBeNotified <- EitherT.liftF(IO(alertsToBeNotified.values.flatMap(elem => elem).map(alert => {
//        val maybeUser = UR.getByUserId(alert.owner)
//        logger.info(s"Maybe user $maybeUser")
//        maybeUser.map(userIO => userIO.map(user => domain.AlertWithUser(alert, user)))
//      }).toList))
//
//      usersToBeNotifiedSeq <- usersToBeNotified.sequence
//      _ <- EitherT.liftF(IO(logger.info(s"usersToBeNotifiedSeq $usersToBeNotifiedSeq")))
//
//      usersToBeNotifiedFlattend <- EitherT.liftF(IO(usersToBeNotifiedSeq.flatten))
//      _ <- EitherT.liftF(IO(logger.info(s"usersToBeNotifiedFlattend $usersToBeNotifiedFlattend")))
//
//      usersToBeNotifiedSnoozeFiltered <- EitherT.liftF(IO(usersToBeNotifiedFlattend.filterNot(f => f.user.snoozeTill > System.currentTimeMillis())))
//      usersToBeNotifiedSnoozeFilteredWithCount <- EitherT.liftF(IO(usersToBeNotifiedSnoozeFiltered.map(f => {
//        val io = notificationsRepository.countNotificationInLastHour(f.alert.id)
//        io.map(x => (f, x))
//      })))
//      EitherT.liftF(usersToBeNotifiedSnoozeFilteredWithCountIO <- toIO(usersToBeNotifiedSnoozeFilteredWithCount))
//      log <- EitherT.liftF(IO(logger.info(s"usersToBeNotifiedSnoozeFilteredWithCountIO $usersToBeNotifiedSnoozeFilteredWithCountIO")))
//
//      alertsByUserNotificationSettings <- EitherT.liftF(IO(usersToBeNotifiedSnoozeFilteredWithCountIO.filter(x => x._2 < x._1.user.notificationsPerHour).map(x => x._1)))
//      //
//      log <- EitherT.liftF(IO(logger.info(s"" +
//        s" $alertsByUserNotificationSettings")))
//      save <- EitherT.liftF(IO(alertsByUserNotificationSettings.map(u => {
//        val beachName = beaches(u.alert.beachId).location
//        tryS(u.alert.beachId, config.surfsUp.notifications.title.replaceAll("BEACH_NAME", beachName),
//          config.surfsUp.notifications.body, u.user, u.alert)
//      }
//      )
//      ))

    } yield ()
  }


  private def tryS(beachId: Long, title: String, body: String, u: User, a: Alert): Either[Exception, String] = {
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

  private def toIOMap(m: Map[EitherT[IO, Exception, domain.Beach], Seq[domain.Alert]]):EitherT[IO, Exception, Map[domain.Beach, Seq[Alert]]]= {
    val x = m.toList.traverse {
      case (io, s) => io.map(s2 => (s2, s))
    }

    x.map {
      _.toMap
    }
  }

}
