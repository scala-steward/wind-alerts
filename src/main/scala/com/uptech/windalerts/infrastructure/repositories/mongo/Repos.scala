package com.uptech.windalerts.infrastructure.repositories.mongo

import com.uptech.windalerts.infrastructure.endpoints.codecs
import org.mongodb.scala.{MongoClient, MongoDatabase}


object Repos{

  def acquireDb(url:String):MongoDatabase = {
    val client = MongoClient(url)
    client.getDatabase(sys.env("projectId")).withCodecRegistry(codecs.codecRegistry)
  }

}
