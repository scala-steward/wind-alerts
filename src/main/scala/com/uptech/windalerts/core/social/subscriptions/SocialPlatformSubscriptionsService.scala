package com.uptech.windalerts.core.social.subscriptions

import cats.data.EitherT
import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.core.user.{UserId, UserT}
import com.uptech.windalerts.infrastructure.endpoints.dtos
import com.uptech.windalerts.infrastructure.endpoints.dtos.{AndroidReceiptValidationRequest, AppleSubscriptionPurchase}

trait SocialPlatformSubscriptionsService[F[_]] {
  def getAndroidPurchase(productId: String, token: String): EitherT[F, SurfsUpError, SubscriptionPurchase]

  def updateAndroidPurchase(user: UserId, request: AndroidReceiptValidationRequest): EitherT[F, SurfsUpError, AndroidToken]

  def getApplePurchase(receiptData: String, password: String): EitherT[F, SurfsUpError, SubscriptionPurchase]

  def updateApplePurchase(user: UserId, req: dtos.ApplePurchaseToken):EitherT[F, SurfsUpError, AppleToken]
}
