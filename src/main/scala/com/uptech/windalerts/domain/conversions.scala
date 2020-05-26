package com.uptech.windalerts.domain

import java.util

import cats.data.EitherT
import cats.effect.IO
import com.uptech.windalerts.domain.domain.UserT
import com.uptech.windalerts.users.ValidationError

import scala.collection.JavaConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

object conversions {

  def toIO[T](x: List[IO[T]]) = {
    import cats.implicits._
    val y: IO[List[T]] = x.sequence

    y
  }

  def toIOSeq[T](x: Seq[IO[T]]):IO[Seq[T]] = {
    toIO(x.toList).map(l=>l.toSeq)
  }


  def generateRandomString(n: Int) = {
    val alpha = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val size = alpha.size

    (1 to n).map(_ => alpha(Random.nextInt.abs % size)).mkString
  }



}
