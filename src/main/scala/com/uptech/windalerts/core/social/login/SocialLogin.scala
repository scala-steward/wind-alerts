package com.uptech.windalerts.core.social.login

import cats.data.EitherT
import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.core.social.subscriptions.SubscriptionPurchase

trait SocialLogin[F[_], T <: AccessRequest] {
  def fetchUserFromPlatform(registerRequest: T): F[SocialUser]
}