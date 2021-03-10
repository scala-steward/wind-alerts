package com.uptech.windalerts.core.credentials

import org.bson.types.ObjectId

trait SocialCredentials {
  def _id: ObjectId

  def email: String

  def socialId: String

  def deviceType: String
}

case class FacebookCredentials(override val _id: ObjectId, override val email: String, override val socialId: String, override val deviceType: String) extends SocialCredentials

object FacebookCredentials {
  def apply(email: String, socialId: String, deviceType: String): FacebookCredentials = new FacebookCredentials(new ObjectId(), email, socialId, deviceType)
}

case class AppleCredentials(override val _id: ObjectId, override val email: String, override val socialId: String, override val deviceType: String) extends SocialCredentials

object AppleCredentials {
  def apply(email: String, socialId: String, deviceType: String): AppleCredentials = new AppleCredentials(new ObjectId(), email, socialId, deviceType)
}