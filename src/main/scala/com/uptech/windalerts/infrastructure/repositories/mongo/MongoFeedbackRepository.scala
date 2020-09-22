package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.domain.Feedback
import com.uptech.windalerts.users.FeedbackRepository
import org.mongodb.scala.MongoCollection

import scala.concurrent.ExecutionContext.Implicits.global

class MongoFeedbackRepository(collection: MongoCollection[Feedback])(implicit cs: ContextShift[IO]) extends FeedbackRepository[IO] {
  override def create(feedback: Feedback): IO[Feedback] =
    IO.fromFuture(IO(collection.insertOne(feedback).toFuture().map(_ => feedback)))
}