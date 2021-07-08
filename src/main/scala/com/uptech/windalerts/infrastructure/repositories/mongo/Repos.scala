package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.effect.{ContextShift, IO}
import com.uptech.windalerts.config.secrets
import com.uptech.windalerts.infrastructure.endpoints.codecs
import com.uptech.windalerts.infrastructure.social.login.{AppleLogin, FacebookLogin}
import org.mongodb.scala.MongoClient

import java.io.File


object Repos{
  def acquireDb() = {
    val client = MongoClient(com.uptech.windalerts.config.secrets.read.surfsUp.mongodb.url)
    client.getDatabase(sys.env("projectId")).withCodecRegistry(codecs.codecRegistry)
  }

  def applePlatform()(implicit cs: ContextShift[IO]) : AppleLogin[IO] = {
    if (new File(s"/app/resources/Apple-${sys.env("projectId")}.p8").exists())
      new AppleLogin(s"/app/resources/Apple-${sys.env("projectId")}.p8")
    else new AppleLogin(s"src/main/resources/Apple.p8")
  }

  def facebookPlatform()(implicit cs: ContextShift[IO]): FacebookLogin[IO] = new FacebookLogin(secrets.read.surfsUp.facebook.key)

}
