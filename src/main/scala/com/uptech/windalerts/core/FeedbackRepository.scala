package com.uptech.windalerts.core

import com.uptech.windalerts.domain.domain.Feedback

trait FeedbackRepository[F[_]] {
  def create(credentials: Feedback): F[Feedback]
}