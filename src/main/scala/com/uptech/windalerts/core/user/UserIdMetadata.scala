package com.uptech.windalerts.core.user

import com.uptech.windalerts.core.types.EmailId

case class UserIdMetadata (userId: UserId, emailId: EmailId, userType: UserType, firstName:String)