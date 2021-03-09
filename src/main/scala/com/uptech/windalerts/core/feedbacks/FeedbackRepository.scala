package com.uptech.windalerts.core.feedbacks

trait FeedbackRepository[F[_]] {
  def create(credentials: Feedback): F[Feedback]
}