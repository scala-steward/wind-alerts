package com.uptech.windalerts.domain

import java.io.File

import com.uptech.windalerts.domain.beaches.Beaches
import io.circe._
import io.circe.config.parser
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.parser.decode
import io.circe.syntax._

import scala.io.Source
import scala.util.{Failure, Success, Try}

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
  implicit val adjustmentEncoder: Encoder[Adjustment] = deriveEncoder
  implicit val adjustmentsDecoder: Decoder[Adjustments] = deriveDecoder
  implicit val adjustmentsEncoder: Encoder[Adjustments] = deriveEncoder

  def read = {
    val tryProd = Try(Source.fromFile("/app/resources/swell-adjustments.json").getLines.mkString)
    val jsonContents = tryProd match {
      case Failure(_) => Source.fromFile("src/main/resources/swell-adjustments.json").getLines.mkString
      case Success(_) => tryProd.get
    }
    Adjustments(decode[Adjustments](jsonContents).toOption.get.adjustments.sortBy(_.from))
  }
}
object statics {
  def read(name:String) = {
    Try(Source.fromFile(s"/app/resources/$name.md").getLines.mkString) match {
      case Failure(_) => Source.fromFile(s"src/main/resources/$name.md").getLines.mkString
      case Success(_) => Try(Source.fromFile(s"/app/resources/$name.md").getLines.mkString).get
    }
  }

  def privacyPolicy = read("privacy-policy")
  def aboutSurfsUp = read("about-surfs-up")
}

object beaches {

  case class Beaches(beaches: Seq[Beach])

  case class Beach(id: Long, location: String, postCode: Long, region: String)

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
    decode[Beaches](jsonContents).toOption.get.beaches.groupBy(_.id).toMap.mapValues(x=>x.head).toMap
  }
}

object A extends App {
  val tryProd = Try(Source.fromFile("/app/resources/beaches-v1.json").getLines.mkString)
  val jsonContents = tryProd match {
    case Failure(_) => Source.fromFile("src/main/resources/beaches-v1.json").getLines.mkString
    case Success(_) => tryProd.get
  }
  val all = decode[Beaches](jsonContents).toOption.get.beaches
  println(Beaches(all.sortBy(f=>f.location)).asJson)
}

object config {

  case class AppConfig(surfsUp: SurfsUp)

  case class SurfsUp(notifications: Notifications)

  case class Notifications(title: String, body: String)

  def read: AppConfig = {
    Option(parser.decodeFile[AppConfig](new File(s"/app/resources/application.conf")).toOption
      .getOrElse(parser.decodeFile[AppConfig](new File(s"src/main/resources/application.conf")).toOption.get)).get
  }
}

object secrets {

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
}