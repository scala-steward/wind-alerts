package com.uptech.windalerts.core.otp

import org.bson.types.ObjectId


case class OTPWithExpiry(_id: ObjectId, otp: String, expiry: Long, userId: String)

object OTPWithExpiry {
  def apply(otp: String, expiry: Long, userId: String): OTPWithExpiry = new OTPWithExpiry(new ObjectId(), otp, expiry, userId)
}