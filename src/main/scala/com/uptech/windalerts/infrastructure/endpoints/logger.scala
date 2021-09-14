package com.uptech.windalerts.infrastructure.endpoints

import cats.Monad
import cats.effect.{Effect, IO}
import org.log4s.getLogger

object logger {
  def requestLogger:Some[String => IO[Unit]] = {
    Some(msg => IO(getLogger.info("Request : " + msg)))
  }
}

class logger[F[_]] {
  def requestLogger(implicit F: Monad[F]):Some[String => F[Unit]] = {
    Some(msg => F.pure(getLogger.info("Request : " + msg)))
  }
}
