package com.uptech.windalerts.core.social.subscriptions

import cats.data.EitherT
import com.uptech.windalerts.domain.domain.{AndroidReceiptValidationRequest, AppleSubscriptionPurchase}
import com.uptech.windalerts.domain.{SurfsUpError, domain}

trait SubscriptionsService[F[_]] {
  def getAndroidPurchase(productId: String, token: String): EitherT[F, SurfsUpError, SubscriptionPurchase]

  def getAndroidPurchase(request: AndroidReceiptValidationRequest): EitherT[F, SurfsUpError, SubscriptionPurchase]

  def getApplePurchase(receiptData: String, password: String): EitherT[F, SurfsUpError, AppleSubscriptionPurchase]
}
