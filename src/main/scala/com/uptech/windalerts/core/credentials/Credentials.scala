package com.uptech.windalerts.core.credentials

import org.bson.types.ObjectId


case class Credentials(_id: ObjectId, email: String, password: String, deviceType: String)

object Credentials {
  def apply(email: String, password: String, deviceType: String): Credentials = new Credentials(new ObjectId(), email, password, deviceType)
}