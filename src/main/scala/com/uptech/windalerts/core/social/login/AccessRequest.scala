package com.uptech.windalerts.core.social.login

sealed trait AccessRequest

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
