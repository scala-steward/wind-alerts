package com.uptech.windalerts.core.social.login

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.core.credentials.{AppleCredentials, FacebookCredentials, SocialCredentials, UserCredentialService}
import com.uptech.windalerts.core.user.{TokensWithUser, UserService, UserT}
import com.uptech.windalerts.core.{SurfsUpError, UserAlreadyExistsError}
import com.uptech.windalerts.infrastructure.endpoints.dtos._
import com.uptech.windalerts.infrastructure.repositories.mongo.Repos
import org.mongodb.scala.bson.ObjectId

class SocialLoginService[F[_] : Sync](repos: Repos[F], userService: UserService[F], credentialService: UserCredentialService[F]) {

  def registerOrLoginAppleUser(credentials: AppleAccessRequest): EitherT[F, SurfsUpError, TokensWithUser] = {
    registerOrLoginUser[AppleAccessRequest, AppleCredentials](credentials,
      repos.applePlatform(),
      socialUser => repos.appleCredentialsRepository().find(socialUser.email, socialUser.deviceType),
      socialUser => repos.appleCredentialsRepository().create(AppleCredentials(socialUser.email, socialUser.socialId, socialUser.deviceType)))
  }


  def registerOrLoginFacebookUser(credentials: FacebookAccessRequest): EitherT[F, SurfsUpError, TokensWithUser] = {
    registerOrLoginUser[FacebookAccessRequest, FacebookCredentials](credentials,
      repos.facebookPlatform(),
      socialUser => repos.facebookCredentialsRepo().find(socialUser.email, socialUser.deviceType),
      socialUser => repos.facebookCredentialsRepo().create(FacebookCredentials(socialUser.email, socialUser.socialId, socialUser.deviceType)))
  }

  def registerOrLoginUser[T <: AccessRequest, U <: SocialCredentials]
  (credentials: T,
   socialPlatform: SocialLogin[F, T],
   credentialFinder: SocialUser => F[Option[U]],
   credentialCreator: SocialUser => F[U]): EitherT[F, SurfsUpError, TokensWithUser] = {
    for {
      socialUser <- EitherT.right(socialPlatform.fetchUserFromPlatform(credentials))
      tokens <- registerOrLoginSocialUser(socialUser, credentialFinder, credentialCreator)
    } yield tokens
  }

  def registerOrLoginSocialUser[T <: SocialCredentials](
                                                         socialUser: SocialUser,
                                                         credentialFinder: SocialUser => F[Option[T]],
                                                         credentialCreator: SocialUser => F[T]): EitherT[F, SurfsUpError, TokensWithUser] = {
    for {
      existingCredential <- EitherT.liftF(credentialFinder(socialUser))
      tokens <- existingCredential.map(_ => tokensForExistingUser(socialUser).leftWiden[SurfsUpError])
        .getOrElse(tokensForNewUser(socialUser, credentialCreator).leftWiden[SurfsUpError])
    } yield tokens
  }

  private def tokensForNewUser[T <: SocialCredentials](socialUser: SocialUser, credentialCreator: SocialUser => F[T]):EitherT[F, UserAlreadyExistsError, TokensWithUser] = {
    for {
      _ <- credentialService.doesNotExist(socialUser.email, socialUser.deviceType)
      result <- EitherT.right(createUser[T](socialUser, credentialCreator))
      tokens <- EitherT.right(userService.generateNewTokens(result._1))
    } yield tokens
  }

  private def tokensForExistingUser[T <: SocialCredentials](socialUser: SocialUser) = {
    for {
      dbUser <- userService.getUser(socialUser.email, socialUser.deviceType)
      tokens <- userService.resetUserSession(dbUser, socialUser.deviceToken)
    } yield tokens
  }

  def createUser[T <: SocialCredentials](user: SocialUser,
                                         credentialCreator: SocialUser => F[T])
  : F[(UserT, SocialCredentials)] = {
    for {
      savedCreds <- credentialCreator(user)
      savedUser <- repos.usersRepo().create(
        UserT.createSocialUser(
          new ObjectId(savedCreds._id.toHexString),
          user.email,
          user.name,
          user.deviceToken,
          user.deviceType))
    } yield (savedUser, savedCreds)
  }

}
