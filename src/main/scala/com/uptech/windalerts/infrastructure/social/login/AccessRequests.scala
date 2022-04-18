package com.uptech.windalerts.infrastructure.social.login

import com.uptech.windalerts.core.social.login.AccessRequest

object AccessRequests {


  case class AppleRegisterRequest(
                                 authorizationCode: String,
                                 nonce: String,
                                 deviceType: String,
                                 deviceToken: String,
                                 name: String) extends AccessRequest

  case class FacebookRegisterRequest(accessToken: String, deviceType: String, deviceToken: String) extends AccessRequest

}
