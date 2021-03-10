package com.uptech.windalerts.core.social.subscriptions

import org.bson.types.ObjectId


case class AppleToken(_id: ObjectId,
                      userId: String,
                      purchaseToken: String,
                      creationTime: Long
                     )

object AppleToken {
  def apply(userId: String, purchaseToken: String, creationTime: Long): AppleToken = new AppleToken(new ObjectId(), userId, purchaseToken, creationTime)
}
