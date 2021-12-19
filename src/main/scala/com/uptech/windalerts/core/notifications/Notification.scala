package com.uptech.windalerts.core.notifications

case class Notification(id: String, alertId: String, userId: String, deviceToken: String, sentAt: Long)