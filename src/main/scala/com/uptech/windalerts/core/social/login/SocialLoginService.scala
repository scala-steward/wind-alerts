package com.uptech.windalerts.core.social.login

import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import cats.mtl.Raise
import com.uptech.windalerts.core.credentials.{SocialCredentials, SocialCredentialsRepository, UserCredentialService}
import com.uptech.windalerts.core.social.SocialPlatformType
import com.uptech.windalerts.core.user.{TokensWithUser, UserRepository, UserService, UserT}
import com.uptech.windalerts.core.{UserAlreadyExistsRegistered, UserNotFoundError}

class SocialLoginService[F[_] : Sync](userRepository: UserRepository[F],
                                      userService: UserService[F],
                                      credentialService: UserCredentialService[F],
                                      socialCredentialsRepositories: Map[SocialPlatformType, SocialCredentialsRepository[F]],
                                      socialLoginProviders: SocialLoginProviders[F]) {
  def registerOrLoginSocialUser(
                                 socialPlatform: SocialPlatformType,
                                 accessToken: String,
                                 deviceType: String,
                                 deviceToken: String,
                                 name: Option[String])(implicit A: Applicative[F], FR: Raise[F, UserAlreadyExistsRegistered], UNF: Raise[F, UserNotFoundError]) = {
    for {
      socialUser <- socialLoginProviders.findByType(socialPlatform)
        .fetchUserFromPlatform(
          accessToken,
          deviceType,
          deviceToken,
          name)
      credentialsRepository = socialCredentialsRepositories(socialPlatform)
      existingCredential <- credentialsRepository.find(socialUser.email, socialUser.deviceType)
      tokens <- existingCredential.map(_ => userService.resetUserSession(socialUser.email, socialUser.deviceType, socialUser.deviceToken))
        .getOrElse(tokensForNewUser(credentialsRepository, socialUser))
    } yield (tokens, existingCredential.isEmpty)
  }

  private def tokensForNewUser[T <: SocialCredentials](credentialsRepository: SocialCredentialsRepository[F], socialUser: SocialUser)(implicit FR: Raise[F, UserAlreadyExistsRegistered]): F[TokensWithUser] = {
    for {
      _ <- credentialService.notRegistered(socialUser.email, socialUser.deviceType)
      result <- createUser(credentialsRepository, socialUser)
      tokens <- userService.generateNewTokens(result._1, socialUser.deviceToken)
    } yield tokens
  }

  def createUser[T <: SocialCredentials](credentialsRepository: SocialCredentialsRepository[F], user: SocialUser)
  : F[(UserT, SocialCredentials)] = {
    for {
      savedCreds <- credentialsRepository.create(user.email, user.socialId, user.deviceType)
      savedUser <- userRepository.create(
        UserT.createSocialUser(savedCreds.id, user.email, user.name, user.deviceType))
    } yield (savedUser, savedCreds)
  }

}
