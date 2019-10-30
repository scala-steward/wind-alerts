package com.uptech.windalerts.domain




import java.io.File

import com.google.common.io.Files
import io.circe._
import io.circe.parser.{decode, _}
import com.uptech.windalerts.domain.config.AppConfig
import io.circe.config.parser
import io.circe.generic.auto._
import io.circe._
import io.circe.generic.semiauto._

import scala.io.Source
import io.circe.parser.decode

import scala.util.{Failure, Success, Try}

object beaches {
  case class Beaches(beaches:Seq[Beach])
  case class Beach(id :Long, location:String, postCode:Long, region:String)
  implicit val beachesDecoder: Decoder[Beaches] = deriveDecoder
  implicit val beachesEncoder: Encoder[Beaches] = deriveEncoder
  implicit val beachDecoder: Decoder[Beach] = deriveDecoder
  implicit val beachEncoder: Encoder[Beach] = deriveEncoder

  def read: Map[Long, Beach] = {
    val tryProd = Try(Source.fromFile("/app/resources/beaches-v1.json").getLines.mkString)
    val jsonContents = tryProd match {
      case Failure(_) => Source.fromFile("src/main/resources/beaches-v1.json").getLines.mkString
      case Success(_) => tryProd.get
    }
    decode[Beaches](jsonContents).toOption.get.beaches.groupBy(_.id).mapValues(v=>v.head)
  }
}

object config {

  case class AppConfig(surfsUp: SurfsUp)

  case class SurfsUp(urls: Urls, notifications: Notifications)

  case class Urls(baseUrl: String)

  case class Notifications(title:String, body:String)

  def read: AppConfig = {
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