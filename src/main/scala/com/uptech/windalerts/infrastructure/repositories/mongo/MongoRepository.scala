package com.uptech.windalerts.infrastructure.repositories.mongo

import cats.Monad
import com.uptech.windalerts.infrastructure.Environment.EnvironmentAsk
import org.mongodb.scala.MongoCollection
import cats.implicits._
import scala.reflect.ClassTag

object MongoRepository {
  def getCollection[F[_], T:ClassTag](name: String)(implicit env: EnvironmentAsk[F], M: Monad[F]): F[MongoCollection[T]] = {
    for {
      db <- env.reader(_.db)
      collection = db.getCollection[T](name)
    } yield collection
  }

}
