package com.uptech.windalerts.core.social.subscriptions

import cats.data.EitherT
import cats.effect.Sync
import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.core.social.SocialPlatformType
import com.uptech.windalerts.core.user.UserId
import com.uptech.windalerts.infrastructure.endpoints.dtos.PurchaseReceiptValidationRequest
import com.uptech.windalerts.infrastructure.social.subscriptions.SocialPlatformSubscriptionsServiceImpl

class SocialPlatformSubscriptionsService[F[_]:Sync](socialPlatformSubscriptionsService: SocialPlatformSubscriptionsServiceImpl[F]) {
  def find(userId: String, deviceType: String): EitherT[F, SurfsUpError, SubscriptionPurchase] = {
    socialPlatformSubscriptionsService.find(userId, deviceType)
  }

  def find(userId: String, platformType: SocialPlatformType): EitherT[F, SurfsUpError, SubscriptionPurchase] = {
    socialPlatformSubscriptionsService.find(userId, platformType)
  }

  def getPurchase(socialPlatformType:SocialPlatformType, token: String): EitherT[F, SurfsUpError, SubscriptionPurchase] = {
    socialPlatformSubscriptionsService.getPurchase(socialPlatformType:SocialPlatformType, token: String)
  }

  def getLatestForToken(socialPlatformType:SocialPlatformType, purchaseToken: String): EitherT[F, SurfsUpError, SubscriptionPurchaseWithUser] = {
    socialPlatformSubscriptionsService.getLatestForToken(socialPlatformType, purchaseToken)
  }

  def handleNewPurchase(socialPlatformType:SocialPlatformType, user: UserId, request: PurchaseReceiptValidationRequest): EitherT[F, SurfsUpError, PurchaseToken] = {
    socialPlatformSubscriptionsService.handleNewPurchase(socialPlatformType, user, request)
  }
}
