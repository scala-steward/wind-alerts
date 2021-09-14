package com.uptech.windalerts.core

import io.circe.Json

case class PublishFailed()

object EventPublisher {
  trait Event {
  }
}

trait EventPublisher[F[_]] {
  def publish(topic: String, event: Json): F[Unit]
}
