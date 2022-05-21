package com.uptech.windalerts.infrastructure.endpoints

import cats.data.OptionT
import cats.effect.Effect
import com.uptech.windalerts.core.user.{UserIdMetadata, UserRepository}
import dev.profunktor.auth.JwtAuthMiddleware
import dev.profunktor.auth.jwt.JwtAuth
import pdi.jwt.{JwtAlgorithm, JwtClaim}
class AuthenticationMiddleware[F[_] : Effect](jwtKey: String, userRepository: UserRepository[F])  {
  val jwtAuth = JwtAuth.hmac(jwtKey, JwtAlgorithm.HS256)

  val authenticate: JwtClaim => F[Option[UserIdMetadata]] = claim => {
    OptionT.fromOption(claim.subject)
      .flatMap(userRepository.getByUserIdOption(_))
      .map(_.userIdMetadata())
      .value
  }


  val middleware = JwtAuthMiddleware[F, UserIdMetadata](jwtAuth, _ => authenticate)
}
