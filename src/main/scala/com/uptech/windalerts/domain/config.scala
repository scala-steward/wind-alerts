package com.uptech.windalerts.domain


import java.io.File

import io.circe.config.parser
import io.circe.generic.auto._

case class AppSettings(surfsup: SurfsUp)

case class SurfsUp(willyWeather: WillyWeather, facebook: Facebook)

case class WillyWeather(key: String)

case class Facebook(key: String)

object config {
  def readConf: AppSettings = {
    val projectId = sys.env("projectId")
    Option(parser.decodeFile[AppSettings](new File(s"/app/resources/$projectId.conf")).toOption
      .getOrElse(parser.decodeFile[AppSettings](new File(s"src/main/resources/application.conf")).toOption.get)).get
  }
}