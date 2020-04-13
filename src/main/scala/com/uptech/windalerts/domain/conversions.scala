package com.uptech.windalerts.domain

import java.util

import cats.effect.IO

import scala.collection.JavaConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object conversions {

  def toIO[T](x: List[IO[T]]) = {
    import cats.implicits._
    val y: IO[List[T]] = x.sequence

    y
  }

  def toIOSeq[T](x: Seq[IO[T]]):IO[Seq[T]] = {
    toIO(x.toList).map(l=>l.toSeq)
  }

}
