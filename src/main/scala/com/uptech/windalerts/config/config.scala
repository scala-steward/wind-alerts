package com.uptech.windalerts.config

import io.circe._
import io.circe.generic.semiauto._

import java.io.File
import scala.io.Source
import scala.util.Try

object config {
  implicit val decoder: Decoder[SurfsUp] = deriveDecoder

  def getConfigFile(name:String):File = {
    val prodFile = new File(s"/app/resources/$name")
    val localFile = new File(s"src/main/resources/$name")

    if (prodFile.exists()) prodFile
    else localFile
  }

  def getConfigFile(name:String, defaultFileName:String):File = {
    val prodFile = new File(s"/app/resources/$name")
    val localFile = new File(s"src/main/resources/$name")

    if (prodFile.exists()) prodFile
    else if (localFile.exists()) localFile
    else new File(s"src/main/resources/$defaultFileName")
  }

  case class AppConfig(surfsUp: SurfsUp)

  case class SurfsUp(notifications: Notifications)

  case class Notifications(title: String, body: String)

}

object swellAdjustments {

  case class Adjustments(adjustments: Seq[Adjustment]) {
    def adjust(height: Double): Double = {
      height *
        adjustments
          .filter(adjustment => adjustment.from <= height && adjustment.to >= height)
          .headOption.map(_.factor).getOrElse(1.0)
    }

  }

  case class Adjustment(from: Double, to: Double, factor: Double)

  implicit val adjustmentDecoder: Decoder[Adjustment] = deriveDecoder
  implicit val adjustmentsDecoder: Decoder[Adjustments] = deriveDecoder

}
object statics {
  def read(name:String) = {
    Try(Source.fromFile(s"/app/resources/$name.md").getLines.mkString)
      .getOrElse(Source.fromFile(s"src/main/resources/$name.md").getLines.mkString)
  }

  def privacyPolicy = read("privacy-policy")
  def aboutSurfsUp = read("about-surfs-up")
}

object beaches {
  case class Beaches(beaches: Seq[Beach]) {
    def toMap() = {
      beaches.groupBy(_.id).toMap.mapValues(x => x.head).toMap
    }
  }

  case class Beach(id: Long, location: String, postCode: Long, region: String)

  implicit val beachesDecoder: Decoder[Beaches] = deriveDecoder
  implicit val beachesEncoder: Encoder[Beaches] = deriveEncoder
  implicit val beachDecoder: Decoder[Beach] = deriveDecoder
  implicit val beachEncoder: Encoder[Beach] = deriveEncoder
}

object secrets {
  implicit val decoder: Decoder[SurfsUp] = deriveDecoder

  case class SecretsSettings(surfsUp: SurfsUp)

  case class SurfsUp(willyWeather: WillyWeather, facebook: Facebook, apple:Apple, email: Email, mongodb: Mongodb)

  case class WillyWeather(key: String)

  case class Facebook(key: String)

  case class Apple(appSecret: String)

  case class Email(apiKey: String)

  case class Mongodb(url: String)

  def getConfigFile():File = {
    val projectId = sys.env("projectId")
    val prodFile = new File(s"/app/resources/secrets-$projectId.conf")
    if (prodFile.exists()) {
      prodFile
    }
    else {
      new File("src/main/resources/secrets.conf")
    }
  }
}