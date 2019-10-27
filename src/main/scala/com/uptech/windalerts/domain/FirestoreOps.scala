package com.uptech.windalerts.domain

import cats.effect.{ContextShift, IO}
import com.google.cloud.firestore
import com.google.cloud.firestore.QueryDocumentSnapshot
import com.uptech.windalerts.domain.conversions.{j2sFuture, j2sMap}

class FirestoreOps(implicit cs: ContextShift[IO]) {
  def getByQuery[T](query: firestore.Query, mf: QueryDocumentSnapshot => T):IO[Seq[T]] = {
    for {
      collection <- IO.fromFuture(IO(j2sFuture(query.get())))
      filtered <- IO(
        j2sMap(collection.getDocuments)
          .map(document => mf(document)
          ))
    } yield filtered
  }

  def getOneByQuery[T](query: firestore.Query, mf: QueryDocumentSnapshot => T): IO[Option[T]] = {
    getByQuery(query, mf).map(_.headOption)
  }
}
