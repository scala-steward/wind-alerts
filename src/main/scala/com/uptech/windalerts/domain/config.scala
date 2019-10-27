package com.uptech.windalerts.domain


import java.io.File

import io.circe.config.parser
import io.circe.generic.auto._


object config {

  case class AppConfig(surfsUp: SurfsUp)

  case class SurfsUp(urls: Urls)

  case class Urls(baseUrl: String)

  def read: AppConfig = {
    val projectId = sys.env("projectId")
    Option(parser.decodeFile[AppConfig](new File(s"/app/resources/application.conf")).toOption
      .getOrElse(parser.decodeFile[AppConfig](new File(s"src/main/resources/application.conf")).toOption.get)).get
  }
}

object secrets {

  case class SecretsSettings(surfsUp: SurfsUp)

  case class SurfsUp(willyWeather: WillyWeather, facebook: Facebook, email: Email)

  case class WillyWeather(key: String)

  case class Facebook(key: String)

  case class Email(userName: String, password: String)

  def read: SecretsSettings = {
    val projectId = sys.env("projectId")
    Option(parser.decodeFile[SecretsSettings](new File(s"/app/resources/$projectId.secrets")).toOption
      .getOrElse(parser.decodeFile[SecretsSettings](new File(s"src/main/resources/secrets.conf")).toOption.get)).get
  }
}