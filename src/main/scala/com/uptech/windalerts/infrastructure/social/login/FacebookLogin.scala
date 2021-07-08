package com.uptech.windalerts.infrastructure.social.login

import cats.Monad
import cats.effect.{Async, ContextShift}
import com.restfb.types.User
import com.restfb.{DefaultFacebookClient, Parameter, Version}
import com.uptech.windalerts.core.social.login.{FacebookAccessRequest, SocialLogin, SocialUser}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class FacebookLogin[F[_]](fbSecret: String)(implicit cs: ContextShift[F], s: Async[F], M: Monad[F])  extends SocialLogin[F, FacebookAccessRequest] {
  override def fetchUserFromPlatform(credentials: FacebookAccessRequest): F[SocialUser] = {
    fetchUserFromPlatform_(credentials)
  }

  private def fetchUserFromPlatform_(credentials: FacebookAccessRequest) = {
    Async.fromFuture(M.pure(Future.successful(new DefaultFacebookClient(credentials.accessToken, fbSecret, Version.LATEST))
      .flatMap(client => Future(client.fetchObject("me", classOf[User], Parameter.`with`("fields", "name,id,email")))
        .map(facebookUser => SocialUser(facebookUser.getId, facebookUser.getEmail, credentials.deviceType, credentials.deviceToken, facebookUser.getFirstName)))))
  }

}