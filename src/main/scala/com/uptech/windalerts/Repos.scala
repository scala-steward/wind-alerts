package com.uptech.windalerts

import cats.Eval
import cats.effect.{ContextShift, IO}
import com.google.api.services.androidpublisher.AndroidPublisher
import com.turo.pushy.apns.auth.ApnsSigningKey
import com.uptech.windalerts.alerts.{AlertsRepositoryT, MongoAlertsRepositoryAlgebra}
import com.uptech.windalerts.domain.beaches.Beach
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.secrets
import com.uptech.windalerts.notifications.{MongoNotificationsRepository, NotificationRepository}
import com.uptech.windalerts.users._
import org.mongodb.scala.{MongoClient, MongoDatabase}

import scala.util.Try

trait Repos[F[_]] {
  def otp(): OtpRepository[F]

  def refreshTokenRepo(): RefreshTokenRepositoryAlgebra[F]

  def usersRepo(): UserRepositoryAlgebra[F]

  def credentialsRepo(): CredentialsRepositoryAlgebra[F]

  def androidPurchaseRepo(): AndroidTokenRepository[F]

  def applePurchaseRepo(): AppleTokenRepository[F]

  def facebookCredentialsRepo(): FacebookCredentialsRepositoryAlgebra[F]

  def appleCredentialsRepository(): AppleCredentialsRepository[F]

  def feedbackRepository(): FeedbackRepository[F]

  def alertsRepository(): AlertsRepositoryT[F]

  def notificationsRepo(): NotificationRepository[F]

  def androidConf(): AndroidPublisher

  def appleLoginConf() : ApnsSigningKey

  def emailConf() : EmailSender[F]

  def fbSecret() : String

  def beaches() : Map[Long, Beach]
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
    new MongoFacebookCredentialsRepository(db.getCollection[FacebookCredentialsT]("facebookCredentials"))
  }

  val appCRepo = Eval.later {
    new MongoAppleCredentialsRepositoryAlgebra(db.getCollection[AppleCredentials]("appleCredentials"))
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

  val appleLogin = Eval.later{
    Try(AppleLogin.getPrivateKey(s"/app/resources/Apple-${sys.env("projectId")}.p8"))
    .getOrElse(AppleLogin.getPrivateKey(s"src/main/resources/Apple.p8"))}

  val email = Eval.later {
    val emailConf = com.uptech.windalerts.domain.secrets.read.surfsUp.email
    new EmailSender[IO](emailConf.userName, emailConf.password)
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

  override def facebookCredentialsRepo: FacebookCredentialsRepositoryAlgebra[IO] = {
    fbRepo.value
  }

  override def appleCredentialsRepository: AppleCredentialsRepository[IO] = {
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

  override def androidConf(): AndroidPublisher = {
    andConf.value
  }

  override def appleLoginConf() = {
    appleLogin.value
  }

  override def emailConf()  = email.value

  override  def fbSecret = fbKey.value

  override  def beaches = b.value
}
