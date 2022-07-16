package com.uptech.windalerts.core

import com.uptech.windalerts.core.social.SocialPlatformType
import com.uptech.windalerts.core.social.login.SocialLoginProvider
import com.uptech.windalerts.core.user.{PasswordNotifier, UserRepository}
import com.uptech.windalerts.core.user.credentials.{CredentialsRepository, SocialCredentialsRepository}
import com.uptech.windalerts.core.user.sessions.UserSessionRepository

case class Infrastructure[F[_]](
                                 jwtKey: String,
                                 userRepository: UserRepository[F],
                                 userSessionsRepository: UserSessionRepository[F],
                                 socialCredentialsRepositories: Map[SocialPlatformType, SocialCredentialsRepository[F]],
                                 socialLoginProviders: Map[SocialPlatformType, SocialLoginProvider[F]],
                                 credentialsRepository: CredentialsRepository[F],
                                 eventPublisher: EventPublisher[F],
                                 passwordNotifier: PasswordNotifier[F])