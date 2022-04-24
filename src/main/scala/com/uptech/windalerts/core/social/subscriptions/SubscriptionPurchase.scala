package com.uptech.windalerts.core.social.subscriptions

import com.uptech.windalerts.core.user.UserId

case class SubscriptionPurchase(startTimeMillis: Long, expiryTimeMillis: Long)

case class SubscriptionPurchaseWithUser(userId: UserId, subscriptionPurchase: SubscriptionPurchase)