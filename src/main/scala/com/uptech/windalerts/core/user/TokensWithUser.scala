package com.uptech.windalerts.core.user

import com.uptech.windalerts.core.user.sessions.UserSessions.UserSession

case class TokensWithUser(accessToken: String, refreshToken: String, expiredAt: Long, user: UserT)
case class Tokens(accessToken: String, refreshToken: UserSession, expiredAt: Long)
