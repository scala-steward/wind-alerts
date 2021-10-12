package com.uptech.windalerts.core.user

import com.uptech.windalerts.core.user.UserType.{Registered, Trial}
import com.uptech.windalerts.infrastructure.endpoints.dtos.UserDTO
import org.bson.types.ObjectId
import io.scalaland.chimney.dsl._


case class UserT(_id: ObjectId, email: String, name: String, deviceType: String, startTrialAt: Long, endTrialAt: Long, userType: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, lastPaymentAt: Long, nextPaymentAt: Long) {
    def firstName() = {
      val firstName = name.split(" ")(0)
      firstName.substring(0, 1).toUpperCase + firstName.substring(1)
    }

    def isTrialEnded() = {
      startTrialAt != -1 && endTrialAt < System.currentTimeMillis()
    }

    def asDTO(): UserDTO = {
      this.into[UserDTO].withFieldComputed(_.id, u => u._id.toHexString).transform
    }
  }

  object UserT {
    def createSocialUser(_id: ObjectId, email: String, name: String, deviceType: String): UserT =
      create(_id, email, name, deviceType, System.currentTimeMillis(), Trial.value)

    def createEmailUser(_id: ObjectId, email: String, name: String, deviceType: String): UserT =
      create(_id, email, name, deviceType, -1, Registered.value)

    def create(_id: ObjectId, email: String, name: String, deviceType: String, startTrialAt: Long, userType: String): UserT =
      UserT(_id, email, name, deviceType, startTrialAt, if (startTrialAt == -1) -1L else (startTrialAt + (30L * 24L * 60L * 60L * 1000L)), userType, -1, false, 4, -1, -1)

    def apply(email: String, name: String, deviceType: String, startTrialAt: Long, endTrialAt: Long, userType: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, lastPaymentAt: Long, nextPaymentAt: Long)
    = new UserT(new ObjectId(), email, name, deviceType, startTrialAt, endTrialAt, userType, snoozeTill, disableAllAlerts, notificationsPerHour, lastPaymentAt, nextPaymentAt)
  }
