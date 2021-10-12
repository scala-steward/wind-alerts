package com.uptech.windalerts.core.social.login

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import com.uptech.windalerts.core.credentials.{AppleCredentials, FacebookCredentials, SocialCredentials, SocialCredentialsRepository, UserCredentialService}
import com.uptech.windalerts.core.user.{TokensWithUser, UserRepository, UserService, UserT}
import com.uptech.windalerts.core.{SurfsUpError, UserAlreadyExistsError}
import com.uptech.windalerts.infrastructure.repositories.mongo.Repos
import com.uptech.windalerts.infrastructure.social.login.{AppleLogin, FacebookLogin}
import org.mongodb.scala.bson.ObjectId

class SocialLoginService[F[_] : Sync](appleLogin: AppleLogin[F],
                                      facebookLogin: FacebookLogin[F],
                                      facebookCredentialsRepo: SocialCredentialsRepository[F, FacebookCredentials],
                                      appleCredentialsRepo: SocialCredentialsRepository[F, AppleCredentials],
                                      userRepository: UserRepository[F],
                                      userService: UserService[F],
                                      credentialService: UserCredentialService[F]) {

  def registerOrLoginAppleUser(credentials: AppleAccessRequest): EitherT[F, SurfsUpError, TokensWithUser] = {
    registerOrLoginUser[AppleAccessRequest, AppleCredentials](credentials,
      appleLogin,
      socialUser => appleCredentialsRepo.find(socialUser.email, socialUser.deviceType),
      socialUser => appleCredentialsRepo.create(AppleCredentials(socialUser.email, socialUser.socialId, socialUser.deviceType)))
  }


  def registerOrLoginFacebookUser(credentials: FacebookAccessRequest): EitherT[F, SurfsUpError, TokensWithUser] = {
    registerOrLoginUser[FacebookAccessRequest, FacebookCredentials](credentials,
      facebookLogin,
      socialUser => facebookCredentialsRepo.find(socialUser.email, socialUser.deviceType),
      socialUser => facebookCredentialsRepo.create(FacebookCredentials(socialUser.email, socialUser.socialId, socialUser.deviceType)))
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
      tokens <- EitherT.right(userService.generateNewTokens(result._1, socialUser.deviceToken))
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
      savedUser <- userRepository.create(
        UserT.createSocialUser(new ObjectId(savedCreds._id.toHexString), user.email, user.name, user.deviceType))
    } yield (savedUser, savedCreds)
  }

}
