package com.uptech.windalerts.core.credentials

import org.bson.types.ObjectId

case class SocialCredentials(val _id: ObjectId, val email: String, val socialId: String, val deviceType: String)

object SocialCredentials {
  def apply(email: String, socialId: String, deviceType: String): SocialCredentials = new SocialCredentials(new ObjectId(), email, socialId, deviceType)
}
