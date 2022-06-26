package com.uptech.windalerts.infrastructure.social

import cats.Monad
import cats.data.OptionT
import cats.mtl.Raise
import com.uptech.windalerts.core.PlatformNotSupported
import com.uptech.windalerts.core.social.SocialPlatformType


object SocialPlatformTypes {
  object Apple extends SocialPlatformType

  object Facebook extends SocialPlatformType

  object Google extends SocialPlatformType

  def apply[F[_]](deviceType: String)(implicit M: Monad[F], PNS: Raise[F, PlatformNotSupported]): F[SocialPlatformType] = {
    OptionT.fromOption[F](findByType(deviceType)).getOrElseF(PNS.raise(PlatformNotSupported()))
  }

  private def findByType(deviceType: String): Option[SocialPlatformType] = deviceType match {
    case "ANDROID" => Some(Google)
    case "IOS" => Some(Apple)
    case _ => None
  }


}