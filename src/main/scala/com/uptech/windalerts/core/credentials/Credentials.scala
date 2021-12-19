package com.uptech.windalerts.core.credentials

import org.bson.types.ObjectId


case class Credentials(id:String, email: String, password: String, deviceType: String)
