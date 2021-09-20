package com.uptech.windalerts.core.notifications

import org.bson.types.ObjectId

case class Notification(_id: ObjectId, alertId: String, userId: String, deviceToken: String, sentAt: Long)

object Notification {
  def apply(alertId: String, userId: String, deviceToken: String, sentAt: Long): Notification
  = new Notification(new ObjectId(), alertId, userId, deviceToken, sentAt)
}