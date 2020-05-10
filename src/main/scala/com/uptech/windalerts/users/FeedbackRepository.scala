package com.uptech.windalerts.users

import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.domain.domain.Feedback
import org.mongodb.scala.MongoCollection

import scala.concurrent.ExecutionContext.Implicits.global

trait FeedbackRepository[F[_]] {
  def create(credentials: Feedback): F[Feedback]
}

class MongoFeedbackRepository(collection: MongoCollection[Feedback])(implicit cs: ContextShift[IO]) extends FeedbackRepository[IO] {
  override def create(feedback: Feedback): IO[Feedback] =
    IO.fromFuture(IO(collection.insertOne(feedback).toFuture().map(_ => feedback)))
}