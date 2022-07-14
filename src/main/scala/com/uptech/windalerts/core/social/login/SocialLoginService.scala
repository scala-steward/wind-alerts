package com.uptech.windalerts.core.social.login

import cats.Applicative
import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import com.uptech.windalerts.core.user.credentials.{SocialCredentials, SocialCredentialsRepository, UserCredentialService}
import com.uptech.windalerts.core.social.SocialPlatformType
import com.uptech.windalerts.core.social.subscriptions.SocialSubscriptionProvider
import com.uptech.windalerts.core.user.sessions.UserSessions
import com.uptech.windalerts.core.user.{Tokens, TokensWithUser, UserRepository, UserService, UserT}
import com.uptech.windalerts.core.{Infrastructure, PlatformNotSupported, UserAlreadyExistsRegistered, UserNotFoundError}

object SocialLoginService {
  def registerOrLoginSocialUser[F[_] : Sync](
                                              socialPlatform: SocialPlatformType,
                                              accessToken: String,
                                              deviceType: String,
                                              deviceToken: String,
                                              name: Option[String])(implicit infrastructure: Infrastructure[F], A: Applicative[F],
                                                                    FR: Raise[F, UserAlreadyExistsRegistered], UNF: Raise[F, UserNotFoundError], PNS: Raise[F, PlatformNotSupported]) = {
    for {
      loginProvider <- getLoginProviderFor(socialPlatform)
      socialUser <- loginProvider
        .fetchUserFromPlatform(accessToken, deviceType, deviceToken, name)
      credentialsRepository = infrastructure.socialCredentialsRepositories(socialPlatform)
      existingCredential <- credentialsRepository.find(socialUser.email, socialUser.deviceType)
      tokens = existingCredential
        .map(credentials => UserSessions.reset(credentials.id, socialUser.deviceToken).flatMap(getTokensWithUser(credentials.id, _)))
      tokensWithUser <- tokens
        .getOrElse(tokensForNewUser(credentialsRepository, socialUser))
    } yield (tokensWithUser, existingCredential.isEmpty)
  }

  def getTokensWithUser[F[_] : Sync](id: String, tokens: Tokens)(implicit infrastructure: Infrastructure[F], FR: Raise[F, UserNotFoundError]): F[TokensWithUser] = {
    for {
      user <- infrastructure.userRepository.getByUserId(id)
      tokensWithUser = TokensWithUser(tokens, user)
    } yield tokensWithUser
  }

  private def tokensForNewUser[F[_] : Sync, T <: SocialCredentials](credentialsRepository: SocialCredentialsRepository[F], socialUser: SocialUser)(implicit infrastructure: Infrastructure[F], FR: Raise[F, UserAlreadyExistsRegistered]): F[TokensWithUser] = {
    for {
      _ <- UserCredentialService.notRegistered(socialUser.email, socialUser.deviceType)
      result <- createUser(credentialsRepository, socialUser)
      tokens <- UserSessions.generateNewTokens(result._1.id, socialUser.deviceToken)
      tokensWithUser = TokensWithUser(tokens, result._1)
    } yield tokensWithUser
  }

  def createUser[F[_] : Sync, T <: SocialCredentials](credentialsRepository: SocialCredentialsRepository[F], user: SocialUser)(implicit infrastructure: Infrastructure[F])
  : F[(UserT, SocialCredentials)] = {
    for {
      savedCreds <- credentialsRepository.create(user.email, user.socialId, user.deviceType)
      savedUser <- infrastructure.userRepository.create(
        UserT.createSocialUser(savedCreds.id, user.email, user.name, user.deviceType))
    } yield (savedUser, savedCreds)
  }

  private def getLoginProviderFor[F[_] : Sync](platformType: SocialPlatformType)(implicit infrastructure: Infrastructure[F], PNS: Raise[F, PlatformNotSupported])
  : F[SocialLoginProvider[F]] = {
    OptionT.fromOption[F](infrastructure.socialLoginProviders.get(platformType)).getOrElseF(PNS.raise(PlatformNotSupported(s"Platform not found $platformType")))
  }
}
