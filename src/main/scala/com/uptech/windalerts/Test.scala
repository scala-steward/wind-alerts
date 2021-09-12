package com.uptech.windalerts

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigFactory.parseFileAnySyntax

import java.io.File

object Test extends App {
  val a = parseFileAnySyntax(new File("beaches-v4.json"))
  print(a.getList("beaches").get(0))
}
