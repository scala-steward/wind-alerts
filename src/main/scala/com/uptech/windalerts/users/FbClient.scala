package com.uptech.windalerts.users

import com.restfb.types.User
import com.restfb.{DefaultFacebookClient, Version}

case object FbClient  extends App {
  val facebookClient = new DefaultFacebookClient("EAAGgtUYMKiQBAMfNGz2gZCZC7AqOCayZChGuoY1R1KrfweVIiVGJ1RsIgMkuhGNew31qZAsmcWVWMZBKyfOSnXQWPy2LCZAY8REHaTVZB0VTm0BY4HTZC5cTGQYrulGJYZBlbeMIjrMLaM2snESSIDjjnzPAMUu0vRD7aYSCUZAtx2GAQ62o3BGL91mChRFWk2bhsUCi0RJTD1NLZC6FooFZBsjoJXR0L7CLqFVnMw8rDEcjIgZDZD",
    "06cee1aa51e14d50c40f28b47a6b7501",
    Version.LATEST);

  import com.restfb.Parameter

  val fbuser = facebookClient.fetchObject("me",classOf[User], Parameter.`with`("fields", "name,id,email"))


  println(fbuser.getEmail)
}
