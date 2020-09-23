package com.uptech.windalerts.social.login

import cats.data.EitherT
import cats.effect.Sync
import com.restfb.{DefaultFacebookClient, Parameter, Version}
import com.uptech.windalerts.Repos
import com.uptech.windalerts.domain.domain.UserType.Trial
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.users.{SocialCredentialsRepository, UserService}
import org.mongodb.scala.bson.ObjectId

class SocialLoginService [F[_] : Sync](repos: Repos[F], userService: UserService[F]) {
  def registerOrLoginFacebookUser(credentials:FacebookRegisterRequest): SurfsUpEitherT[F, TokensWithUser] = {
    for {
      facebookClient <- EitherT.pure(new DefaultFacebookClient(credentials.accessToken, repos.fbSecret(), Version.LATEST))
      facebookUser <- EitherT.pure(facebookClient.fetchObject("me", classOf[com.restfb.types.User], Parameter.`with`("fields", "name,id,email")))
      repo = repos.facebookCredentialsRepo()
      socialUser = SocialUser(facebookUser.getId, facebookUser.getEmail, credentials.deviceType, credentials.deviceToken, facebookUser.getFirstName)
      tokens <- registerOrLoginSocialUser[FacebookCredentials](repo, socialUser, u=>FacebookCredentials(u.email, u.socialId, u.deviceType))
    } yield tokens
  }


  def registerOrLoginAppleUser(rr:AppleRegisterRequest): SurfsUpEitherT[F, TokensWithUser] = {
    for {
      appleUser <- EitherT.pure(AppleLogin.getUser(rr.authorizationCode, repos.appleLoginConf()))
      repo = repos.appleCredentialsRepository()
      socialUser = SocialUser(appleUser.sub, appleUser.email, rr.deviceType, rr.deviceToken, rr.name)
      tokens <- registerOrLoginSocialUser[AppleCredentials](repo, socialUser, u=>AppleCredentials(u.email, u.socialId, u.deviceType))
    } yield tokens
  }

  def registerOrLoginSocialUser[T<:SocialCredentials](repo:SocialCredentialsRepository[F, T], socialUser: SocialUser, credentialCreator:SocialUser=>T): SurfsUpEitherT[F, TokensWithUser] = {
    for {
      existingCredential <- EitherT.liftF(repo.find(socialUser.email, socialUser.deviceType))
      tokens <- {
        if (existingCredential.isEmpty) {
          for {
            _ <- userService.doesNotExist(socialUser.email, socialUser.deviceType)
            result <- createUser[T](socialUser, repo, credentialCreator)
            tokens <- userService.generateNewTokens(result._1)
          } yield tokens
        } else {
          for {
            dbUser <- userService.getUser(socialUser.email, socialUser.deviceType)
            tokens <- userService.resetUserSession(dbUser, socialUser.deviceToken)
          } yield tokens
        }
      }
    } yield tokens
  }

  def createUser[T<:SocialCredentials](user: SocialUser, repo:SocialCredentialsRepository[F, T], credentialCreator:SocialUser=>T): SurfsUpEitherT[F, (UserT, SocialCredentials)] = {
    for {
      savedCreds <- EitherT.liftF(repo.create(credentialCreator.apply(user)))
      savedUser <- EitherT.liftF(repos.usersRepo().create(UserT.create(new ObjectId(savedCreds._id.toHexString), user.email,
        user.name,  user.deviceToken, user.deviceType, System.currentTimeMillis(), Trial.value, -1, false, 4)))
    } yield (savedUser, savedCreds)
  }

}
