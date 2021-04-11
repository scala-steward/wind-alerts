package com.uptech.windalerts.core.social.login

import cats.data.EitherT
import com.uptech.windalerts.core.social.subscriptions.SubscriptionPurchase
import com.uptech.windalerts.domain.SurfsUpError

trait SocialLogin[F[_], T <: AccessRequest] {
  def fetchUserFromPlatform(registerRequest: T): F[SocialUser]
}