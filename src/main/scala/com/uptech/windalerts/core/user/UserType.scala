package com.uptech.windalerts.core.user


sealed case class UserType(value: String) {
  def isPremiumUser(): Boolean = {
    this == UserType.Premium || this == UserType.Trial
  }
}

object UserType {

  object Registered extends UserType("Registered")

  object PremiumExpired extends UserType("PremiumExpired")

  object Trial extends UserType("Trial")

  object TrialExpired extends UserType("TrialExpired")

  object Premium extends UserType("Premium")

  val values = Seq(Registered, PremiumExpired, Trial, TrialExpired, Premium)

  def apply(value: String): UserType = value match {
    case Registered.value => Registered
    case PremiumExpired.value => PremiumExpired
    case Trial.value => Trial
    case TrialExpired.value => TrialExpired
    case Premium.value => Premium
  }


}