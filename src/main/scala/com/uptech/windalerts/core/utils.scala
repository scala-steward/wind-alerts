package com.uptech.windalerts.core

import scala.util.Random

object utils {
  def generateRandomString(size: Int) = {
    val alpha = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    (1 to size).map(_ => alpha(Random.nextInt.abs % alpha.size)).mkString
  }
}
