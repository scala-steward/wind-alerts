package com.uptech.windalerts.core.social.subscriptions

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import com.uptech.windalerts.core.{PlatformNotSupported, TokenNotFoundError}
import com.uptech.windalerts.core.social.SocialPlatformType
import com.uptech.windalerts.core.types._
import com.uptech.windalerts.core.user.UserId

class SubscriptionService[F[_] : Sync](socialSubscriptionProviders: Map[SocialPlatformType, SocialSubscriptionProvider[F]] = Map(), purchaseTokenRepositories: Map[SocialPlatformType, PurchaseTokenRepository[F]] = Map()) {

  def handleNewPurchase(user: UserId, req: PurchaseReceiptValidationRequest, platformType: SocialPlatformType)(implicit FR: Raise[F, TokenNotFoundError], PNS: Raise[F, PlatformNotSupported]) = {
    for {
      provider <- getSubscriptionProviderFor(platformType)
      _ <- provider.getPurchaseFromPlatform(req.token)
      repo <- getPurchaseTokenRepositoryFor(platformType)
      savedToken <- repo.create(user.id, req.token, System.currentTimeMillis())
    } yield savedToken
  }

  def findForUser(userId: String, platformType: SocialPlatformType)
                 (implicit FR: Raise[F, TokenNotFoundError], PNS: Raise[F, PlatformNotSupported]): F[SubscriptionPurchase] = {
    for {
      purchaseTokenRepository <- getPurchaseTokenRepositoryFor(platformType)
      token <- purchaseTokenRepository.getLastForUser(userId)
      subscriptionProvider <- getSubscriptionProviderFor(platformType)
      purchase <- subscriptionProvider.getPurchaseFromPlatform(token.purchaseToken)
    } yield purchase
  }


  def getLatestForToken(purchaseToken: String, platformType: SocialPlatformType)(implicit FR: Raise[F, TokenNotFoundError], PNS: Raise[F, PlatformNotSupported]): F[SubscriptionPurchaseWithUser] = {
    for {
      repo <- getPurchaseTokenRepositoryFor(platformType)
      purchaseToken <- repo.getByToken(purchaseToken)
      provider <- getSubscriptionProviderFor(platformType)
      subscriptionPurchase <- provider.getPurchaseFromPlatform(purchaseToken.purchaseToken)
    } yield SubscriptionPurchaseWithUser(UserId(purchaseToken.userId), subscriptionPurchase)
  }

  private def getPurchaseTokenRepositoryFor(platformType: SocialPlatformType)(implicit PNS: Raise[F, PlatformNotSupported]): F[PurchaseTokenRepository[F]] = {
    OptionT.fromOption[F](purchaseTokenRepositories.get(platformType))
      .getOrElseF(PNS.raise(PlatformNotSupported(s"Platform not found $platformType")))
  }

  private def getSubscriptionProviderFor(platformType: SocialPlatformType)(implicit PNS: Raise[F, PlatformNotSupported])
  : F[SocialSubscriptionProvider[F]] = {
    OptionT.fromOption[F](socialSubscriptionProviders.get(platformType)).getOrElseF(PNS.raise(PlatformNotSupported(s"Platform not found $platformType")))
  }
}