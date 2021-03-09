package com.uptech.windalerts.core.feedbacks

import org.bson.types.ObjectId


case class Feedback(_id: ObjectId, topic: String, message: String, userId: String)

object Feedback {
  def apply(topic: String, message: String, userId: String): Feedback = new Feedback(new ObjectId, topic, message, userId)
}
