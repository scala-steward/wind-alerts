package com.uptech.windalerts.infrastructure

import cats.Applicative.ops.toAllApplicativeOps
import cats.Monad
import cats.effect.{Async, ContextShift}
import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.{PubsubMessage, TopicName}
import com.uptech.windalerts.core.EventPublisher
import io.circe.Json

import scala.concurrent.ExecutionContext.Implicits.global


class GooglePubSubEventpublisher[F[_]](projectId: String)
                                      (implicit cs: ContextShift[F], s: Async[F], M: Monad[F])
  extends EventPublisher[F] {
  override def publish(topic: String, event: Json): F[Unit] = {
    val topicName = TopicName.of(projectId, topic)

    val publisher = Publisher.newBuilder(topicName).build()
    val message = event.toString()
    val data = ByteString.copyFromUtf8(message)
    val pubsubMessage = PubsubMessage.newBuilder.setData(data).build

    Async.fromFuture(M.pure(concurrent.Future {
      publisher.publish(pubsubMessage).get()
    })).map(_ => ())
  }
}