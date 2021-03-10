package com.uptech.windalerts.infrastructure.social.login

import cats.data.EitherT
import cats.effect.{ContextShift, IO}
import com.restfb.types.User
import com.restfb.{DefaultFacebookClient, Parameter, Version}
import com.uptech.windalerts.core.social.login.{FacebookAccessRequest, SocialPlatform}
import com.uptech.windalerts.domain.UserNotFoundError
import com.uptech.windalerts.domain.domain.{SocialUser, SurfsUpEitherT}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class FacebookPlatform(fbSecret: String)(implicit cs: ContextShift[IO]) extends SocialPlatform[IO, FacebookAccessRequest] {
  override def fetchUserFromPlatform(credentials: FacebookAccessRequest): SurfsUpEitherT[IO, SocialUser] = {
    fetchUserFromPlatform_(credentials)
      .leftMap(_ => UserNotFoundError())
  }

  private def fetchUserFromPlatform_(credentials: FacebookAccessRequest): SurfsUpEitherT[IO, SocialUser] = {
    EitherT.liftF(IO.fromFuture(IO(Future.successful(new DefaultFacebookClient(credentials.accessToken, fbSecret, Version.LATEST))
      .flatMap(client => Future(client.fetchObject("me", classOf[User], Parameter.`with`("fields", "name,id,email")))
        .map(facebookUser => SocialUser(facebookUser.getId, facebookUser.getEmail, credentials.deviceType, credentials.deviceToken, facebookUser.getFirstName))))))
  }
}