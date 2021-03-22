package com.uptech.windalerts.infrastructure.endpoints

import cats.effect.IO
import org.log4s.getLogger

object logger {
  def requestLogger:Some[String => IO[Unit]] = {
    Some(msg => IO(getLogger.error("Request : " + msg)))
  }
}
