package com.uptech.windalerts.core.social.subscriptions

import org.bson.types.ObjectId

case class PurchaseToken(_id: ObjectId,
                         userId: String,
                         subscriptionId: String,
                         purchaseToken: String,
                         creationTime: Long
                        )
object PurchaseToken {
  def apply(userId: String,
            subscriptionId: String,
            purchaseToken: String,
            creationTime: Long) = new PurchaseToken(new ObjectId(), userId, subscriptionId, purchaseToken, creationTime)

  def apply(userId: String,
            purchaseToken: String,
            creationTime: Long) = new PurchaseToken(new ObjectId(), userId, "", purchaseToken, creationTime)
}
