package com.uptech.windalerts.domain

import io.circe.syntax._

import java.io.File

import com.uptech.windalerts.domain.beaches.{Beach, Beaches}
import io.circe._
import io.circe.config.parser
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.parser.decode

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
    decode[Beaches](jsonContents).toOption.get.beaches.groupBy(_.id).mapValues(v => v.head)
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