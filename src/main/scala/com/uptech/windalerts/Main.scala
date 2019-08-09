package com.uptech.windalerts

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._

object Main extends IOApp {
  def run(args: List[String]) =
    WindalertsServer.stream[IO].compile.drain.as(ExitCode.Success)
}