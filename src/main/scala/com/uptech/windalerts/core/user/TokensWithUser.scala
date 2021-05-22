package com.uptech.windalerts.core.user

case class TokensWithUser(accessToken: String, refreshToken: String, expiredAt: Long, user: UserT)
