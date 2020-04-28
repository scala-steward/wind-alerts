package com.uptech.windalerts.domain

import cats.effect.IO
import org.log4s.getLogger

object logger {
  def requestLogger:Some[String => IO[Unit]] = {
    Some(msg => IO(getLogger.error(msg)))
  }
}
