package com.uptech.windalerts.core

import cats.effect.{Async, IO, Sync}
import cats.implicits._

import scala.util.Try
object Program extends App {

  val program = new Program[IO]

  val y = program.x()
  print(y)

  val s = y.unsafeRunSync()
  print(s.mkString(""))
}

class Program[F[_] : Sync] {

  def x()(implicit F: Async[F]) = {
    import cats.syntax.flatMap._
    import cats.syntax.functor._

    for {
      i<-F.pure(Iterator.range(1, 100))
      j <- i.map(k=>y(k)).toList.sequence

    } yield j

  }

  def y(x:Int)(implicit F: Async[F]) = {

    F.delay {
      Try {
        if (x % 2 == 0)
          throw new IllegalArgumentException
        else
          x
      }
    }
  }

  }