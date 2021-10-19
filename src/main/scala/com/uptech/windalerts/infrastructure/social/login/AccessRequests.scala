package com.uptech.windalerts.infrastructure.social.login

import com.uptech.windalerts.core.social.login.AccessRequest

object AccessRequests {
  case class FacebookAccessRequest(
                                    accessToken: String,
                                    deviceType: String,
                                    deviceToken: String) extends AccessRequest

  case class AppleAccessRequest(
                                 authorizationCode: String,
                                 nonce: String,
                                 deviceType: String,
                                 deviceToken: String,
                                 name: String) extends AccessRequest
}
