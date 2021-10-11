package com.uptech.windalerts.core.refresh.tokens

import com.uptech.windalerts.core.utils
import org.bson.types.ObjectId


case class UserSession(_id: ObjectId, refreshToken: String, expiry: Long, userId: String) {
  def isExpired() = System.currentTimeMillis() > expiry
}

object UserSession {
  val REFRESH_TOKEN_EXPIRY = 14L * 24L * 60L * 60L * 1000L

  def apply(userId: String) = new UserSession(new ObjectId(), utils.generateRandomString(40), System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY, userId)
}
