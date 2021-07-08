package com.uptech.windalerts.infrastructure.social.subscriptions

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.core.social.subscriptions.{AppleTokenRepository, _}
import com.uptech.windalerts.core.user.UserId
import com.uptech.windalerts.infrastructure.endpoints.dtos._
import com.uptech.windalerts.infrastructure.repositories.mongo.Repos

class SubscriptionsServiceImpl[F[_] : Sync](applePurchaseRepository: AppleTokenRepository[F], androidPurchaseRepository: AndroidTokenRepository[F], appleSubscription: SocialSubscription[F], androidSubscription: SocialSubscription[F]) extends SubscriptionsService[F] {

  override def updateAndroidPurchase(user: UserId, request: AndroidReceiptValidationRequest): EitherT[F, SurfsUpError, AndroidToken] = {
    for {
      _ <- getAndroidPurchase(request.productId, request.token)
      savedToken <- androidPurchaseRepository.create(AndroidToken(user.id, request.productId, request.token, System.currentTimeMillis())).leftWiden[SurfsUpError]
    } yield savedToken
  }

  override def getAndroidPurchase(productId: String, token: String): EitherT[F, SurfsUpError, SubscriptionPurchase] = {
    EitherT.right(androidSubscription.getPurchase(token, productId))
  }

  override def updateApplePurchase(user: UserId, req: ApplePurchaseToken): EitherT[F, SurfsUpError, AppleToken] = {
    for {
      _ <- getApplePurchase(req.token, "")
      savedToken <- applePurchaseRepository.create(AppleToken(user.id, req.token, System.currentTimeMillis()))
    } yield savedToken
  }


  override def getApplePurchase(receiptData: String, password: String): EitherT[F, SurfsUpError, SubscriptionPurchase] = {
    EitherT.right(appleSubscription.getPurchase(receiptData, "" ))
  }

}
