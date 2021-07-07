package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.Eval
import cats.effect.{ContextShift, IO}
import com.google.api.services.androidpublisher.AndroidPublisher
import com.uptech.windalerts.config.beaches.Beach
import com.uptech.windalerts.config.secrets
import com.uptech.windalerts.core.alerts.AlertsRepositoryT
import com.uptech.windalerts.core.alerts.domain.Alert
import com.uptech.windalerts.core.credentials._
import com.uptech.windalerts.core.feedbacks.{Feedback, FeedbackRepository}
import com.uptech.windalerts.core.notifications.{Notification, NotificationRepository}
import com.uptech.windalerts.core.social.login.{AppleAccessRequest, FacebookAccessRequest, SocialLogin}
import com.uptech.windalerts.core.social.subscriptions.{AndroidToken, AndroidTokenRepository, AppleToken, AppleTokenRepository}
import com.uptech.windalerts.infrastructure.endpoints.codecs
import com.uptech.windalerts.infrastructure.social.login.{AppleLogin, FacebookLogin}
import com.uptech.windalerts.infrastructure.social.subscriptions.{AndroidPublisherHelper, ApplicationConfig}
import org.mongodb.scala.{MongoClient, MongoDatabase}

import java.io.File

trait Repos[F[_]] {

  def androidPurchaseRepo(): AndroidTokenRepository[F]

  def applePurchaseRepo(): AppleTokenRepository[F]

  def feedbackRepository(): FeedbackRepository[F]

  def alertsRepository(): AlertsRepositoryT[F]

  def notificationsRepo(): NotificationRepository[F]

  def androidPublisher(): AndroidPublisher

  def fbSecret() : String

  def applePlatform(): SocialLogin[F, AppleAccessRequest]

  def facebookPlatform(): SocialLogin[F, FacebookAccessRequest]

}

object Repos{
  def acquireDb() = {
    val start = System.currentTimeMillis()
    val value = Eval.later {
      val client = MongoClient(com.uptech.windalerts.config.secrets.read.surfsUp.mongodb.url)
      client.getDatabase(sys.env("projectId")).withCodecRegistry(codecs.codecRegistry)
    }
    val v = value.value
    println(System.currentTimeMillis() - start)
    v
  }
}
class LazyRepos(implicit cs: ContextShift[IO]) extends Repos[IO] {

  var  maybeDb:MongoDatabase = _


  val andRepo = Eval.later {
    new MongoAndroidPurchaseRepository(db.getCollection[AndroidToken]("androidPurchases"))
  }

  val appRepo = Eval.later {
    new MongoApplePurchaseRepository(db.getCollection[AppleToken]("applePurchases"))
  }


  val fdRepo = Eval.later {
    new MongoFeedbackRepository(db.getCollection[Feedback]("feedbacks"))
  }

  val alRepo = Eval.later {
    new MongoAlertsRepositoryAlgebra(db.getCollection[Alert]("alerts"))
  }

  val nRepo = Eval.later {
    new MongoNotificationsRepository(db.getCollection[Notification]("notifications"))
  }

  val andConf = Eval.later(AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL))


  val fbKey = Eval.later {
    secrets.read.surfsUp.facebook.key
  }

  override def androidPurchaseRepo: AndroidTokenRepository[IO] = {
    andRepo.value
  }

  override def applePurchaseRepo: AppleTokenRepository[IO] = {
    appRepo.value
  }

  override def feedbackRepository: FeedbackRepository[IO] = {
    fdRepo.value
  }

  override def alertsRepository = {
    alRepo.value
  }

  override def notificationsRepo = {
    nRepo.value
  }

  private def db() = {

    if (maybeDb == null)
      maybeDb = Eval.later{Repos.acquireDb}.value
    maybeDb
  }

  override def androidPublisher(): AndroidPublisher = {
    andConf.value
  }

  override  def fbSecret = fbKey.value

  override def applePlatform(): SocialLogin[IO, AppleAccessRequest] = {
    if (new File(s"/app/resources/Apple-${sys.env("projectId")}.p8").exists())
      new AppleLogin(s"/app/resources/Apple-${sys.env("projectId")}.p8")
    else new AppleLogin(s"src/main/resources/Apple.p8")
  }

  override def facebookPlatform(): SocialLogin[IO, FacebookAccessRequest] = new FacebookLogin(fbKey.value)
}
