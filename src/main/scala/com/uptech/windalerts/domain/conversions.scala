package com.uptech.windalerts.domain

import java.util

import cats.effect.IO

import scala.collection.JavaConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object conversions {
  def j2sFuture[A](javaFuture: util.concurrent.Future[A]): Future[A] = {
    Future(javaFuture.get())
  }

  def j2sMap[A](inputList: util.List[A]): Seq[A] = JavaConverters.asScalaIteratorConverter(inputList.iterator).asScala.toSeq

  def j2sm[K, V](map: util.Map[K, V]): Map[K, V] = JavaConverters.mapAsScalaMap(map).toMap

  def toIO[T](x: List[IO[T]]) = {
    import cats.implicits._

    val y: IO[List[T]] = x.sequence

    y
  }
}
