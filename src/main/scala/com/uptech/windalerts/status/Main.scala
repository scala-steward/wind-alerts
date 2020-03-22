package com.uptech.windalerts.status

import cats.effect._

object Main extends IOApp {
  System.setProperty("user.timezone", "Australia/Sydney")
  override def run(args: List[String]): IO[ExitCode] =
    Backend.start[IO].use(_ => IO.never)
}