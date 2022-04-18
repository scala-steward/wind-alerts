package com.uptech.windalerts.core

import com.uptech.windalerts.core.types.UserRegistered
import io.circe.Json

case class PublishFailed()

object EventPublisher {
  trait Event
}

trait EventPublisher[F[_]] {
  def publishUserRegistered(topic: String, message: UserRegistered): F[Unit]
}
