package com.uptech.windalerts.infrastructure.social

import com.uptech.windalerts.core.social.SocialPlatformType


object SocialPlatformTypes {
  object Apple extends SocialPlatformType

  object Facebook extends SocialPlatformType

  object Google extends SocialPlatformType

  def apply(deviceType:String): Option[SocialPlatformType] = deviceType match {
    case "ANDROID" => Some(Google)
    case "IOS" => Some(Apple)
    case _ => None
  }
}