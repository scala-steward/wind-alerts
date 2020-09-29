package com.uptech.windalerts

import java.io.File

import cats.Eval
import cats.effect.{ContextShift, IO}
import com.google.api.services.androidpublisher.AndroidPublisher
import com.uptech.windalerts.alerts.AlertsRepositoryT
import com.uptech.windalerts.alerts.domain.AlertT
import com.uptech.windalerts.domain.beaches.Beach
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.secrets
import com.uptech.windalerts.infrastructure.EmailSender
import com.uptech.windalerts.infrastructure.repositories.mongo._
import com.uptech.windalerts.infrastructure.social.login.{ApplePlatform, FacebookPlatform}
import com.uptech.windalerts.notifications.NotificationRepository
import com.uptech.windalerts.social.login.domain
import com.uptech.windalerts.social.login.domain.SocialPlatform
import com.uptech.windalerts.social.subcriptions.{AndroidPublisherHelper, AndroidTokenRepository, AppleTokenRepository}
import com.uptech.windalerts.users._
import org.mongodb.scala.{MongoClient, MongoDatabase}

trait Repos[F[_]] {
  def otp(): OtpRepository[F]

  def refreshTokenRepo(): RefreshTokenRepositoryAlgebra[F]

  def usersRepo(): UserRepositoryAlgebra[F]

  def credentialsRepo(): CredentialsRepositoryAlgebra[F]

  def androidPurchaseRepo(): AndroidTokenRepository[F]

  def applePurchaseRepo(): AppleTokenRepository[F]

  def facebookCredentialsRepo(): SocialCredentialsRepository[F, FacebookCredentials]

  def appleCredentialsRepository(): SocialCredentialsRepository[F, AppleCredentials]

  def feedbackRepository(): FeedbackRepository[F]

  def alertsRepository(): AlertsRepositoryT[F]

  def notificationsRepo(): NotificationRepository[F]

  def androidPublisher(): AndroidPublisher

  def emailConf() : EmailSender[F]

  def fbSecret() : String

  def beaches() : Map[Long, Beach]

  def applePlatform(): SocialPlatform[F, com.uptech.windalerts.social.login.domain.AppleAccessRequest]

  def facebookPlatform(): SocialPlatform[F, com.uptech.windalerts.social.login.domain.FacebookAccessRequest]

}


class LazyRepos(implicit cs: ContextShift[IO]) extends Repos[IO] {

  var  maybeDb:MongoDatabase = _

  val otpRepo = Eval.later {
    new MongoOtpRepository(db.getCollection[OTPWithExpiry]("otp"))
  }

  val refreshRepo = Eval.later {
    new MongoRefreshTokenRepositoryAlgebra(db.getCollection[RefreshToken]("refreshTokens"))
  }

  val uRepo = Eval.later {
    val start = System.currentTimeMillis()
    val x = new MongoUserRepository(db.getCollection[UserT]("users"))
    println(System.currentTimeMillis() - start)
    x
  }

  val cRepo = Eval.later {
    new MongoCredentialsRepository(db.getCollection[Credentials]("credentials"))
  }

  val andRepo = Eval.later {
    new MongoAndroidPurchaseRepository(db.getCollection[AndroidToken]("androidPurchases"))
  }

  val appRepo = Eval.later {
    new MongoApplePurchaseRepository(db.getCollection[AppleToken]("applePurchases"))
  }

  val fbRepo = Eval.later {
    new MongoSocialCredentialsRepository[FacebookCredentials](db.getCollection[FacebookCredentials]("facebookCredentials"))
  }

  val appCRepo = Eval.later {
    new MongoSocialCredentialsRepository[AppleCredentials](db.getCollection[AppleCredentials]("appleCredentials"))
  }

  val fdRepo = Eval.later {
    new MongoFeedbackRepository(db.getCollection[Feedback]("feedbacks"))
  }

  val alRepo = Eval.later {
    new MongoAlertsRepositoryAlgebra(db.getCollection[AlertT]("alerts"))
  }

  val nRepo = Eval.later {
    new MongoNotificationsRepository(db.getCollection[Notification]("notifications"))
  }

  val andConf = Eval.later(AndroidPublisherHelper.init(ApplicationConfig.APPLICATION_NAME, ApplicationConfig.SERVICE_ACCOUNT_EMAIL))


  val email = Eval.later {
    val emailConf = com.uptech.windalerts.domain.secrets.read.surfsUp.email
    new EmailSender[IO](emailConf.apiKey)
  }

  val fbKey = Eval.later {
    secrets.read.surfsUp.facebook.key
  }

  val b = Eval.later {
    com.uptech.windalerts.domain.beaches.read
  }

  override def otp(): OtpRepository[IO] = {
    otpRepo.value
  }

  override def refreshTokenRepo: RefreshTokenRepositoryAlgebra[IO] = {
    refreshRepo.value
  }

  override def usersRepo: UserRepositoryAlgebra[IO] = {
    uRepo.value
  }

  override def credentialsRepo: CredentialsRepositoryAlgebra[IO] = {
    cRepo.value
  }

  override def androidPurchaseRepo: AndroidTokenRepository[IO] = {
    andRepo.value
  }

  override def applePurchaseRepo: AppleTokenRepository[IO] = {
    appRepo.value
  }

  override def facebookCredentialsRepo: SocialCredentialsRepository[IO, FacebookCredentials] = {
    fbRepo.value
  }

  override def appleCredentialsRepository:  SocialCredentialsRepository[IO, AppleCredentials] = {
    appCRepo.value
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

  private def acquireDb() = {
    val start = System.currentTimeMillis()
    val value = Eval.later {
      val client = MongoClient(com.uptech.windalerts.domain.secrets.read.surfsUp.mongodb.url)
      client.getDatabase(sys.env("projectId")).withCodecRegistry(com.uptech.windalerts.domain.codecs.codecRegistry)
    }
    val v = value.value
    println(System.currentTimeMillis() - start)
    v
  }

  private def db() = {

    if (maybeDb == null)
      maybeDb = Eval.later{acquireDb}.value
    maybeDb
  }

  override def androidPublisher(): AndroidPublisher = {
    andConf.value
  }

  override def emailConf()  = email.value

  override  def fbSecret = fbKey.value

  override  def beaches = b.value


  override def applePlatform(): SocialPlatform[IO, domain.AppleAccessRequest] = {
    if (new File(s"/app/resources/Apple-${sys.env("projectId")}.p8").exists())
      new ApplePlatform(s"/app/resources/Apple-${sys.env("projectId")}.p8")
    else new ApplePlatform(s"src/main/resources/Apple.p8")
  }

  override def facebookPlatform(): SocialPlatform[IO, domain.FacebookAccessRequest] = new FacebookPlatform(fbKey.value)
}
