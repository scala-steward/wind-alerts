package com.uptech.windalerts.core.refresh.tokens


case class UserSession(id: String, refreshToken: String, expiry: Long, userId: String, deviceToken:String) {
  def isExpired() = System.currentTimeMillis() > expiry
}

object UserSession {
  val REFRESH_TOKEN_EXPIRY = 14L * 24L * 60L * 60L * 1000L
}
