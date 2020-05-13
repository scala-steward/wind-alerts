package com.uptech.windalerts.status

import cats.effect._
import org.log4s.getLogger

object Main extends IOApp {
  val started = System.currentTimeMillis()
  sys.addShutdownHook(getLogger.error(s"Shutting down after ${(System.currentTimeMillis() - started)} ms"))
  override def run(args: List[String]): IO[ExitCode] =
    Backend.start[IO].use(_ => IO.never)
}