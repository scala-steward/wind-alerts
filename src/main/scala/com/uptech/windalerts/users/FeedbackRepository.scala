package com.uptech.windalerts.users

import com.uptech.windalerts.domain.domain.Feedback

trait FeedbackRepository[F[_]] {
  def create(credentials: Feedback): F[Feedback]
}