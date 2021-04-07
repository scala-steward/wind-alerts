package com.uptech.windalerts.infrastructure.social.subscriptions

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.softwaremill.sttp.{HttpURLConnectionBackend, sttp, _}
import com.uptech.windalerts.Repos
import com.uptech.windalerts.core.social.subscriptions.{AndroidToken, AppleToken, SubscriptionPurchase, SubscriptionsService}
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.{SurfsUpError, UnknownError, secrets}
import io.circe.optics.JsonPath.root
import io.circe.parser
import io.circe.syntax._
import io.scalaland.chimney.dsl._
import org.log4s.getLogger

class SubscriptionsServiceImpl[F[_] : Sync](repos: Repos[F]) extends SubscriptionsService[F] {

  override def getAndroidPurchase(request: AndroidReceiptValidationRequest): EitherT[F, SurfsUpError, SubscriptionPurchase] = {
    getAndroidPurchase(request.productId, request.token)
  }

  override def getAndroidPurchase(productId: String, token: String): EitherT[F, SurfsUpError, SubscriptionPurchase] = {
    EitherT.pure({
      repos.androidPublisher().purchases().subscriptions().get(ApplicationConfig.PACKAGE_NAME, productId, token).execute().into[SubscriptionPurchase].enableBeanGetters
        .withFieldComputed(_.expiryTimeMillis, _.getExpiryTimeMillis.toLong)
        .withFieldComputed(_.startTimeMillis, _.getStartTimeMillis.toLong).transform
    })
  }

  override def getApplePurchase(receiptData: String, password: String): EitherT[F, SurfsUpError, AppleSubscriptionPurchase] = {
    implicit val backend = HttpURLConnectionBackend()

    val json = ApplePurchaseVerificationRequest(receiptData, password, true).asJson.toString()
    val req = sttp.body(json).contentType("application/json")
      .post(uri"https://sandbox.itunes.apple.com/verifyReceipt")

    EitherT.fromEither(
      req
        .send().body
        .left.map(UnknownError(_))
        .flatMap(json => {
          getLogger.error(s"Json from apple $json")
          parser.parse(json)
        })
        .map(root.receipt.in_app.each.json.getAll(_))
        .flatMap(_.map(p => p.as[AppleSubscriptionPurchase])
          .filter(_.isRight).maxBy(_.right.get.expires_date_ms))
        .left.map(e => UnknownError(e.getMessage))
    )
  }

  def getAndroidPurchase(user: UserId, request: AndroidReceiptValidationRequest):EitherT[F, SurfsUpError, AndroidToken] = {
    for {
      _ <- getAndroidPurchase(request)
      savedToken <- repos.androidPurchaseRepo().create(AndroidToken(user.id, request.productId, request.token, System.currentTimeMillis()))
    } yield savedToken
  }


  override def updateApplePurchase(user: UserId, req: ApplePurchaseToken):EitherT[F, SurfsUpError, AppleToken] = {
    for {
      _ <- getApplePurchase(req.token, secrets.read.surfsUp.apple.appSecret)
      savedToken <- repos.applePurchaseRepo().create(AppleToken(user.id, req.token, System.currentTimeMillis()))
    } yield savedToken
  }

}
