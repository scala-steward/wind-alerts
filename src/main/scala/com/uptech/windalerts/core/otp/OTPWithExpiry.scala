package com.uptech.windalerts.core.otp


case class OTPWithExpiry(id: String, otp: String, expiry: Long, userId: String)
