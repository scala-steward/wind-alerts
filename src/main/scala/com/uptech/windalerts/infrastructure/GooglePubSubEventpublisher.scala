package com.uptech.windalerts.infrastructure

import cats.Monad
import cats.effect.{Async, ContextShift, Resource}
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

    val future = concurrent.Future {
        publisher.publish(pubsubMessage).get()
    }.map(_ => {
      ()
    })
    future.onComplete(_=>publisher.shutdown())
    Async.fromFuture(M.pure(future))
  }
}