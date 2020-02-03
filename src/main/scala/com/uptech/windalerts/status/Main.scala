package com.uptech.windalerts.status

import cats.effect._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Backend.start[IO].use(_ => IO.never)
}