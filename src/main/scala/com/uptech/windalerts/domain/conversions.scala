package com.uptech.windalerts.domain

import cats.effect.IO

import scala.util.Random

object conversions {

  def toIO[T](x: List[IO[T]]): IO[List[T]] = {
    import cats.implicits._
    x.sequence
  }

  def toIOSeq[T](x: Seq[IO[T]]):IO[Seq[T]] = {
    toIO(x.toList).map(_.toSeq)
  }


  def generateRandomString(n: Int) = {
    val alpha = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val size = alpha.size

    (1 to n).map(_ => alpha(Random.nextInt.abs % size)).mkString
  }
}
