package com.uptech.windalerts.core.user

import com.uptech.windalerts.infrastructure.endpoints.dtos.EmailId

case class UserIdMetadata (userId: UserId, emailId: EmailId, userType: UserType, firstName:String)