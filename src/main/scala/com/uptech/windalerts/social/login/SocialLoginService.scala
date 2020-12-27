package com.uptech.windalerts.social.login

import cats.data.EitherT
import cats.effect.Sync
import com.uptech.windalerts.Repos
import com.uptech.windalerts.domain.domain.UserType.Trial
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.social.login.domain.SocialPlatform
import com.uptech.windalerts.users.{SocialCredentialsRepository, UserService}
import org.mongodb.scala.bson.ObjectId

class SocialLoginService[F[_] : Sync](repos: Repos[F], userService: UserService[F]) {

  def registerOrLoginAppleUser(credentials: domain.AppleAccessRequest): SurfsUpEitherT[F, TokensWithUser] = {
    registerOrLoginUser[domain.AppleAccessRequest, AppleCredentials](credentials,
      repos.applePlatform(),
      socialUser => repos.appleCredentialsRepository().find(socialUser.email, socialUser.deviceType),
      socialUser => repos.appleCredentialsRepository().create(AppleCredentials(socialUser.email, socialUser.socialId, socialUser.deviceType)))
  }


  def registerOrLoginFacebookUser(credentials: domain.FacebookAccessRequest): SurfsUpEitherT[F, TokensWithUser] = {
    registerOrLoginUser[domain.FacebookAccessRequest, FacebookCredentials](credentials,
      repos.facebookPlatform(),
      socialUser => repos.facebookCredentialsRepo().find(socialUser.email, socialUser.deviceType),
      socialUser => repos.facebookCredentialsRepo().create(FacebookCredentials(socialUser.email, socialUser.socialId, socialUser.deviceType)))
  }

  def registerOrLoginUser[T <: com.uptech.windalerts.social.login.domain.AccessRequest, U <: SocialCredentials]
  (credentials: T,
   socialPlatform: SocialPlatform[F, T],
   credentialFinder: SocialUser => F[Option[U]],
   credentialCreator: SocialUser => F[U]): SurfsUpEitherT[F, TokensWithUser] = {
    for {
      socialUser <- socialPlatform.fetchUserFromPlatform(credentials)
      tokens <- registerOrLoginSocialUser(socialUser, credentialFinder, credentialCreator)
    } yield tokens
  }

  def registerOrLoginSocialUser[T <: SocialCredentials](
                                                         socialUser: SocialUser,
                                                         credentialFinder: SocialUser => F[Option[T]],
                                                         credentialCreator: SocialUser => F[T]): SurfsUpEitherT[F, TokensWithUser] = {
    for {
      existingCredential <- EitherT.liftF(credentialFinder(socialUser))
      tokens <- existingCredential.map(_ => tokensForExistingUser(socialUser))
        .getOrElse(tokensForNewUser(socialUser, credentialCreator))
    } yield tokens
  }

  private def tokensForNewUser[T <: SocialCredentials](socialUser: SocialUser, credentialCreator: SocialUser => F[T]) = {
    for {
      _ <- userService.doesNotExist(socialUser.email, socialUser.deviceType)
      result <- createUser[T](socialUser, credentialCreator)
      tokens <- userService.generateNewTokens(result._1)
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
  : SurfsUpEitherT[F, (UserT, SocialCredentials)] = {
    for {
      savedCreds <- EitherT.liftF(credentialCreator(user))
      savedUser <- EitherT.liftF(repos.usersRepo().create(
        UserT.createSocialUser(
          new ObjectId(savedCreds._id.toHexString),
          user.email,
          user.name,
          user.deviceToken,
          user.deviceType)))
    } yield (savedUser, savedCreds)
  }

}
