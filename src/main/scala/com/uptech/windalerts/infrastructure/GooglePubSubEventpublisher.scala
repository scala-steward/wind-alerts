package com.uptech.windalerts.infrastructure

import cats.Monad
import cats.effect.{Async, ContextShift, Resource}
import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.{PubsubMessage, TopicName}
import com.uptech.windalerts.core.EventPublisher
import com.uptech.windalerts.core.types.UserRegistered
import com.uptech.windalerts.infrastructure.endpoints.codecs.userRegisteredEncoder
import io.circe.syntax.EncoderOps

import scala.concurrent.ExecutionContext.Implicits.global


class GooglePubSubEventpublisher[F[_]](projectId: String)
                                      (implicit cs: ContextShift[F], s: Async[F], M: Monad[F])
  extends EventPublisher[F] {
  override def publishUserRegistered(topic: String, userRegistered: UserRegistered): F[Unit] = {
    val topicName = TopicName.of(projectId, topic)

    val publisher = Publisher.newBuilder(topicName).build()
    val data = ByteString.copyFromUtf8(userRegistered.asJson.toString)
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