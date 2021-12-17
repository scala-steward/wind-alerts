package com.uptech.windalerts.core.social.login

import cats.Applicative
import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.core.credentials.{SocialCredentials, SocialCredentialsRepository, UserCredentialService}
import com.uptech.windalerts.core.social.SocialPlatformType
import com.uptech.windalerts.core.user.{TokensWithUser, UserRepository, UserService, UserT}
import com.uptech.windalerts.core.{SurfsUpError, UnknownError, UserAlreadyExistsError, UserNotFoundError}
import org.mongodb.scala.bson.ObjectId
import cats.Monad
import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.infrastructure.EmailSender
import com.uptech.windalerts.infrastructure.endpoints.codecs._
import com.uptech.windalerts.infrastructure.endpoints.dtos._
import com.uptech.windalerts.infrastructure.repositories.mongo.DBUser
import io.circe.parser.parse

import scala.util.Random

class SocialLoginService[F[_] : Sync](userRepository: UserRepository[F],
                                      userService: UserService[F],
                                      credentialService: UserCredentialService[F],
                                      socialCredentialsRepositories: Map[SocialPlatformType, SocialCredentialsRepository[F]],
                                      socialLoginProviders: SocialLoginProviders[F]) {
  def registerOrLoginSocialUser(socialPlatform: SocialPlatformType, accessRequest: AccessRequest)(implicit A:Applicative[F]) = {
    for {
      socialUser <- EitherT.right(socialLoginProviders.fetchUserFromPlatform(accessRequest))
      credentialsRepository <- EitherT.fromEither(socialCredentialsRepositories(socialPlatform).asRight)(A)
      existingCredential <- EitherT.liftF(credentialsRepository.find(socialUser.email, socialUser.deviceType))
      tokens <- existingCredential.map(_ => userService.resetUserSession(socialUser.email, socialUser.deviceType, socialUser.deviceToken).leftWiden[SurfsUpError])
        .getOrElse(tokensForNewUser(credentialsRepository, socialUser).leftWiden[SurfsUpError])
    } yield (tokens, existingCredential.isEmpty)
  }

  private def tokensForNewUser[T <: SocialCredentials](credentialsRepository: SocialCredentialsRepository[F], socialUser: SocialUser):EitherT[F, UserAlreadyExistsError, TokensWithUser] = {
    for {
      _ <- credentialService.doesNotExist(socialUser.email, socialUser.deviceType)
      result <- EitherT.right(createUser(credentialsRepository, socialUser))
      tokens <- EitherT.right(userService.generateNewTokens(result._1, socialUser.deviceToken))
    } yield tokens
  }

  def createUser[T <: SocialCredentials](credentialsRepository: SocialCredentialsRepository[F], user: SocialUser)
  : F[(UserT, SocialCredentials)] = {
    for {
      savedCreds <- credentialsRepository.create(SocialCredentials(user.email, user.socialId, user.deviceType))
      savedUser <- userRepository.create(
        UserT.createSocialUser(savedCreds._id.toHexString, user.email, user.name, user.deviceType))
    } yield (savedUser, savedCreds)
  }

}
