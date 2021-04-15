package com.uptech.windalerts.infrastructure.social.login

import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.restfb.types.User
import com.restfb.{DefaultFacebookClient, Parameter, Version}
import com.uptech.windalerts.core.SurfsUpError
import com.uptech.windalerts.core.social.login.{FacebookAccessRequest, SocialLogin, SocialUser}
import com.uptech.windalerts.core.social.subscriptions.SubscriptionPurchase

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class FacebookLogin(fbSecret: String)(implicit cs: ContextShift[IO]) extends SocialLogin[IO, FacebookAccessRequest] {
  override def fetchUserFromPlatform(credentials: FacebookAccessRequest): IO[SocialUser] = {
    fetchUserFromPlatform_(credentials)
  }

  private def fetchUserFromPlatform_(credentials: FacebookAccessRequest) = {
    IO.fromFuture(IO(Future.successful(new DefaultFacebookClient(credentials.accessToken, fbSecret, Version.LATEST))
      .flatMap(client => Future(client.fetchObject("me", classOf[User], Parameter.`with`("fields", "name,id,email")))
        .map(facebookUser => SocialUser(facebookUser.getId, facebookUser.getEmail, credentials.deviceType, credentials.deviceToken, facebookUser.getFirstName)))))
  }

}