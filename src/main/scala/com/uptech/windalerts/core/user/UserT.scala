package com.uptech.windalerts.core.user

import cats.data.EitherT
import com.google.common.base.Strings
import com.uptech.windalerts.core.UserNotFoundError
import com.uptech.windalerts.core.user.UserType.{Premium, PremiumExpired, Registered, Trial}
import com.uptech.windalerts.infrastructure.endpoints.dtos.{EmailId, UserDTO}
import io.scalaland.chimney.dsl._


case class UserT(id: String, email: String, name: String, deviceType: String, startTrialAt: Long, endTrialAt: Long, userType: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, lastPaymentAt: Long, nextPaymentAt: Long) {
  def firstName() = {
    if (Strings.isNullOrEmpty(name))
      ""
    else {
      val firstName = name.split(" ")(0)
      firstName.substring(0, 1).toUpperCase + firstName.substring(1)
    }
  }

  def isTrialEnded() = {
    startTrialAt != -1 && endTrialAt < System.currentTimeMillis()
  }

  def asDTO(): UserDTO = {
    this.into[UserDTO].withFieldComputed(_.id, u => u.id).transform
  }

  def userIdMetadata() = UserIdMetadata(UserId(id), EmailId(email), UserType(userType), firstName)

  def makeTrial() = {
    copy(startTrialAt = System.currentTimeMillis(), endTrialAt = System.currentTimeMillis() + (30L * 24L * 60L * 60L * 1000L), userType = Trial.value)
  }

  def makePremium(start: Long, expiry: Long) = {
    copy(userType = Premium.value, lastPaymentAt = start, nextPaymentAt = expiry)
  }

  def makePremiumExpired() = {
    copy(userType = PremiumExpired.value, nextPaymentAt = -1)
  }

  def makeTrialExpired() = {
    copy(userType = UserType.TrialExpired.value, lastPaymentAt = -1, nextPaymentAt = -1)
  }

}

object UserT {
  def createSocialUser(id: String, email: String, name: String, deviceType: String): UserT =
    create(id, email, name, deviceType, System.currentTimeMillis(), Trial.value)

  def createEmailUser(id: String, email: String, name: String, deviceType: String): UserT =
    create(id, email, name, deviceType, -1, Registered.value)

  def create(id: String, email: String, name: String, deviceType: String, startTrialAt: Long, userType: String): UserT =
    UserT(id, email, fixName(name), deviceType, startTrialAt, if (startTrialAt == -1) -1L else (startTrialAt + (30L * 24L * 60L * 60L * 1000L)), userType, -1, false, 4, -1, -1)

  def fixName(name: String) = {
    if (name == null)
      ""
    else name
  }
}
