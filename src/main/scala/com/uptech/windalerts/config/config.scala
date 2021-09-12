package com.uptech.windalerts.config

import com.uptech.windalerts.config.beaches.Beaches
import com.uptech.windalerts.config.secrets.SurfsUp
import io.circe._
import io.circe.config.parser
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.parser.decode

import java.io.File
import scala.io.Source
import scala.util.{Failure, Success, Try}

object config {
  implicit val decoder: Decoder[SurfsUp] = deriveDecoder

  def getConfigFile(name:String):File = {
    val prodFile = new File(s"/app/resources/$name")
    if (prodFile.exists()) prodFile
    else {
      new File(s"src/main/resources/$name")
    }
  }
  case class AppConfig(surfsUp: SurfsUp)

  case class SurfsUp(notifications: Notifications)

  case class Notifications(title: String, body: String)

  def read: AppConfig = {
    Option(parser.decodeFile[AppConfig](new File(s"/app/resources/application.conf")).toOption
      .getOrElse(parser.decodeFile[AppConfig](new File(s"src/main/resources/application.conf")).toOption.get)).get
  }
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

  def read = {
    val tryProd = Try(Source.fromFile("/app/resources/swell-adjustments.json").getLines.mkString)
    val json = tryProd.getOrElse(Source.fromFile("src/main/resources/swell-adjustments.json").getLines.mkString)

    Adjustments(decode[Adjustments](json).toOption.get.adjustments.sortBy(_.from))
  }
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

  def read(): Map[Long, Beach] = {
    val tryProd = Try(Source.fromFile("/app/resources/beaches-v4.json").getLines.mkString)
    val jsonContents = tryProd match {
      case Failure(_) => Source.fromFile("src/main/resources/beaches-v4.json").getLines.mkString
      case Success(_) => tryProd.get
    }
    val beaches1 = decode[Beaches](jsonContents).toOption.get.beaches
    beaches1.groupBy(_.id).toMap.mapValues(x => x.head).toMap
  }

}

object A extends App {
  val tryProd = Try(Source.fromFile("/app/resources/beaches-v4.json").getLines.mkString)
  val jsonContents = tryProd match {
    case Failure(_) => Source.fromFile("src/main/resources/beaches-v4.json").getLines.mkString
    case Success(_) => tryProd.get
  }
  val all = decode[Beaches](jsonContents).toOption.get.beaches
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

  def read: SecretsSettings = {
    val projectId = sys.env("projectId")
    import org.log4s.getLogger
    getLogger.error(projectId)
    getLogger.error("File exists " + new File(s"/app/resources/$projectId.secrets").exists())
    getLogger.error("File exists " + new File(s"src/main/resources/$projectId.secrets").exists())

    Option(parser.decodeFile[SecretsSettings](new File(s"/app/resources/$projectId.secrets")).toOption
      .getOrElse(parser.decodeFile[SecretsSettings](new File(s"src/main/resources/secrets.conf")).toOption.get)).get
  }

  def getConfigFile():File = {
    val projectId = sys.env("projectId")
    import org.log4s.getLogger
    getLogger.error(projectId)
    getLogger.error("File exists " + new File(s"/app/resources/$projectId.secrets").exists())
    getLogger.error("File exists " + new File(s"src/main/resources/$projectId.secrets").exists())

    val prodFile = new File(s"/app/resources/$projectId.secrets")
    if (prodFile.exists()) prodFile
    else {
      new File("src/main/resources/secrets.conf")
    }
  }
}