package com.uptech.windalerts.core.user

import com.uptech.windalerts.core.user.sessions.UserSessions.UserSession

case class TokensWithUser(accessToken: String, refreshToken: String, expiredAt: Long, user: UserT)
object TokensWithUser {
  def apply(tokens: Tokens, user:UserT): TokensWithUser =
    new TokensWithUser(tokens.accessToken, tokens.refreshToken.refreshToken, tokens.expiredAt, user)
}
case class Tokens(accessToken: String, refreshToken: UserSession, expiredAt: Long)
