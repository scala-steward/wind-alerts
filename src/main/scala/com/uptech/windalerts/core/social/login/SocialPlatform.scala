package com.uptech.windalerts.core.social.login

import com.uptech.windalerts.domain.domain.{SurfsUpEitherT}

trait SocialPlatform[F[_], T <: AccessRequest] {
  def fetchUserFromPlatform(registerRequest: T): SurfsUpEitherT[F, SocialUser]
}