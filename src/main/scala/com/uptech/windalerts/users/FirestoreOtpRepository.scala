package com.uptech.windalerts.users

import java.util

import cats.data.{EitherT, OptionT}
import cats.effect.{ContextShift, IO}
import com.google.cloud.firestore
import com.google.cloud.firestore.{CollectionReference, Firestore, QueryDocumentSnapshot}
import com.uptech.windalerts.domain.conversions._
import com.uptech.windalerts.domain.domain.UserType.Registered
import com.uptech.windalerts.domain.domain.{OTPWithExpiry, RegisterRequest, User}
import com.uptech.windalerts.domain.{FirestoreOps, domain}

import scala.beans.BeanProperty

class FirestoreOtpRepository(db: Firestore, dbops: FirestoreOps)(implicit cs: ContextShift[IO]) extends OtpRepository {
  private val collection: CollectionReference = db.collection("otp")

  override def exists(otp: String, userId: String): EitherT[IO, OtpNotFoundError, OTPWithExpiry] = {
    EitherT.fromOptionF(getByQuery(
      collection.whereEqualTo("otp", otp)
        .whereEqualTo("userId", userId)
      //.whereLessThan("expiry", System.currentTimeMillis())),
    ),
      OtpNotFoundError())
  }

  override def create(otp: domain.OTPWithExpiry): IO[domain.OTPWithExpiry] = {
    for {
      _ <- IO.fromFuture(IO(j2sFuture(collection.add(toBean(otp)))))
    } yield otp
  }


  private def toBean(otp: domain.OTPWithExpiry) = {
    new OtpBean(otp.otp, otp.expiry, otp.userId)
  }


  def getByQuery(query: firestore.Query): IO[Option[OTPWithExpiry]] = {
    val mf: QueryDocumentSnapshot => OTPWithExpiry = document => {
      val OTPWithExpiry(otp) = (document.getId, j2sm(document.getData).asInstanceOf[Map[String, util.HashMap[String, String]]])
      otp
    }
    dbops.getOneByQuery(query, mf)
  }

}

class OtpBean(
               @BeanProperty var otp: String,
               @BeanProperty var expiry: Long,
               @BeanProperty var userId: String) {}
