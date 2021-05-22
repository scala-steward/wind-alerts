package com.uptech.windalerts.infrastructure.social.subscriptions

import cats.data.EitherT
import cats.effect.Async
import cats.implicits._
import com.softwaremill.sttp.{HttpURLConnectionBackend, sttp, _}
import com.uptech.windalerts.core.social.subscriptions.{SocialSubscription, SubscriptionPurchase}
import com.uptech.windalerts.infrastructure.endpoints.codecs._
import com.uptech.windalerts.infrastructure.endpoints.dtos.{ApplePurchaseVerificationRequest, AppleSubscriptionPurchase}
import com.uptech.windalerts.config.secrets
import io.circe.optics.JsonPath.root
import io.circe.parser
import io.circe.syntax._
import org.log4s.getLogger
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.core.UnknownError

import scala.concurrent.ExecutionContext

class AppleSubscription[F[_] : Async](implicit F: Async[F]) extends SocialSubscription[F] {

  override def getPurchase(receiptData: String, productId: String): F[SubscriptionPurchase] = {
    implicit val backend = HttpURLConnectionBackend()

    val json = ApplePurchaseVerificationRequest(receiptData, secrets.read.surfsUp.apple.appSecret, true).asJson.toString()
    val req = sttp.body(json).contentType("application/json")
      .post(uri"https://sandbox.itunes.apple.com/verifyReceipt")

    F.delay(
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
        .map(purchase => SubscriptionPurchase(purchase.purchase_date_ms, purchase.expires_date_ms))
        .left.map(e => UnknownError(e.getMessage)).right.get
    )
  }
}
