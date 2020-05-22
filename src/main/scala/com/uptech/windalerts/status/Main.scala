package com.uptech.windalerts.status

import cats.effect._
import com.uptech.windalerts.LazyRepos
import com.uptech.windalerts.domain.beaches.Beaches
import org.log4s.getLogger

object Main extends IOApp {
  val started = System.currentTimeMillis()
  sys.addShutdownHook(getLogger.error(s"Shutting down after ${(System.currentTimeMillis() - started)} ms"))
  override def run(args: List[String]): IO[ExitCode] = {
    val repos = new LazyRepos()
    Backend.start[IO](repos).use(_ => IO.never)
  }
}